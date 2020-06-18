# 콘서트 예매 시스템

# Table of contents

- [서비스 시나리오](#서비스-시나리오)
- [분석/설계](#분석설계)
- [구현](#구현)
  - [DDD 의 적용](#ddd-의-적용)
  - [Gateway 적용](#Gateway-적용)
  - [동기식 호출 과 Fallback 처리](#동기식-호출-과-Fallback-처리)
  - [비동기식 호출 과 Eventual Consistency](#비동기식-호출-과-Eventual-Consistency)
- [운영](#운영)
  - [CI/CD 설정](#cicd설정)
  - [서킷 브레이킹 / 장애격리](#서킷-브레이킹-/-장애격리)
  - [오토스케일 아웃](#오토스케일-아웃)
  - [무정지 재배포](#무정지-재배포)
  

# 서비스 시나리오

기능적 요구사항
1. 관리자가 콘서트를 등록한다.
1. 사용자가 회원가입을 한다.
1. 사용자가 콘서트를 예약한다.
1. 사용자가 예약한 콘서트를 결제한다.
1. 결제가 완료되면 콘서트예약이 승인된다.
1. 콘서트예약이 승인되면 티켓 수가 변경된다. (감소)
1. 사용자가 예약 취소를 하면 결제가 취소되고 티켓 수가 변경된다. (증가)
1. 사용자가 콘서트 예약내역 상태를 조회한다.

비기능적 요구사항
1. 트랜잭션
    1. 결제가 되지 않은 콘서트은 승인되지 않는다. > Sync 
1. 장애격리
    1. 콘서트 관리 기능이 수행되지 않아도 콘서트 예약은 수행된다. > Async (event-driven)
    1. Payment시스템이 과중되면 결제를 잠시후에 하도록 유도한다. > Circuit breaker, Fallback
1. 성능
    1. 사용자는 콘서트 예약내역을 확인할 수 있다. > CQRS


# 분석/설계

## Event Storming 결과

<img src="https://user-images.githubusercontent.com/62231786/84783162-e5903a80-b023-11ea-9fb6-ffc453c534cf.png"/>

<img src="https://user-images.githubusercontent.com/62231786/84783192-ef19a280-b023-11ea-9208-d5dd9246c6ce.jpg"/>

```
# 도메인 서열
- Core : Booking
- Supporting : Concert, User
- General : Payment
```


## 헥사고날 아키텍처 다이어그램 도출
    
![image](https://user-images.githubusercontent.com/62231786/84896907-c0f99880-b0df-11ea-9b34-fc4f9cb2934b.png)


# 구현

## DDD 의 적용

분석/설계 단계에서 도출된 MSA는 총 6개로 아래와 같다.
* View 는 CQRS 를 위한 서비스

| MSA | 기능 | port | 조회 API |
|---|:---:|:---:|:---:|
| Concert | 콘서트 관리 | 8081 | http://localhost:8081/cooncerts |
| Booking | 예약 관리 | 8082 | http://localhost:8082/booings |
| Payment | 결제 관리 | 8083 | http://localhost:8083/payments |
| User | 사용자 관리 | 8084 | http://localhost:8084/users |
| MyPage | 콘서트 예약내역 관리 | 8086 | http://localhost:8085/myPage |


## Gateway 적용

```
spring:
  cloud:
    gateway:
      routes:
        - id: course
          uri: http://localhost:8081
          predicates:
            - Path=/courses/**
        - id: lecture
          uri: http://localhost:8082
          predicates:
            - Path=/lectures/**
        - id: certification
          uri: http://localhost:8083
          predicates:
            - Path=/certifications/**
        - id: payment
          uri: http://localhost:8084
          predicates:
            - Path=/payments/**
        - id: student
          uri: http://localhost:8085
          predicates:
            - Path=/students/**
        - id: view
          uri: http://localhost:8086
          predicates:
            - Path=/view/**
```


## 동기식 호출 과 Fallback 처리

수강신청(Lecture) > 결제(Payment) 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리

- FeignClient 서비스 구현

```
# PaymentService.java

@FeignClient(name="payment", url="${feign.payment.url}", fallback = PaymentServiceFallback.class)
public interface PaymentService {
    @RequestMapping(method = RequestMethod.POST, path="/payments")
    public void enroll(@RequestBody Payment payment);
}
```


- 동기식 호출

```
# Lecture.java

@PostPersist
    public void onPostPersist() {
        Payment payment = new Payment();
        payment.setCourseId(this.courseId);
        payment.setStudentId(this.studentId);
        payment.setStatus("Payment Requested");

        ResponseEntity studentResponse = LectureApplication.applicationContext.getBean(StudentService.class).selectOne(this.studentId);
        ResponseEntity courseResponse = LectureApplication.applicationContext.getBean(CourseService.class).selectOne(this.courseId);

        if(courseResponse.getStatusCode().equals(HttpStatus.OK) && studentResponse.getStatusCode().equals(HttpStatus.OK)) {
            LectureApplication.applicationContext.getBean(PaymentService.class).enroll(payment);
        }
        else {
            log.info("Payment Request Error : Check CourseId And StudentId");
        }
    }
```

- 동기식 호출 테스트

```
# 결제서비스 중단

# 수강신청
http POST http://localhost:8082/lectures studentId=1 courseId=1 status=requested   #Fail

# 결제서비스 재기동

# 수강신청
http POST http://localhost:8082/lectures studentId=1 courseId=1 status=requested   #Success
```

- Fallback 서비스 구현

```
# PaymentServiceFallback.java

@Component
public class PaymentServiceFallback implements PaymentService {

	@Override
	public void enroll(Payment payment) {
		System.out.println("Circuit breaker has been opened. Fallback returned instead.");
	}

}
```


## 비동기식 호출 과 Fallback 처리

- 비동기식 발신 구현

```
# Course.java

@PreRemove
public void onPreRemove() {
    CourseDeleted courseDeleted = new CourseDeleted();
    BeanUtils.copyProperties(this, courseDeleted);
    courseDeleted.publishAfterCommit();
}
```

- 비동기식 수신 구현

```
# PolicyHandler.java

@StreamListener(KafkaProcessor.INPUT)
public void wheneverLectureCompleted(@Payload LectureCompleted lectureCompleted) {
    if(lectureCompleted.isMe()) {
        Certification certification = new Certification();
        certification.setCourseId(lectureCompleted.getCourseId());
        certification.setStatus("Certified");
        certification.setStudentId(lectureCompleted.getStudentId());

        certificationRepository.save(certification);
    }
}
```

- 비동기식 호출 테스트

```
# 수료 서비스 중단

# 수강완료
http PATCH http://localhost:8082/lectures/1 status=completed

# 수료 상태 조회 > 수료 데이터 없음

# 수료 서비스 기동

# 수료 조회
"certifications": [
  {
    "studentId": 1,
    "courseId": 1,
    "status": "Certified",
    "_links": {
      "self": {
        "href": "http://localhost:8083/certifications/1"
      },
      "certification": {
        "href": "http://localhost:8083/certifications/1"
      }
    }
  }
]
```


# 운영

## CI/CD 설정

- CodeBuild 기반으로 파이프라인 구성

<img src="https://user-images.githubusercontent.com/62231786/84913102-1c825100-b0f5-11ea-9b92-5119abc18415.png"/>

- Git Hook 연경

<img src="https://user-images.githubusercontent.com/62231786/84975759-38bbd780-b161-11ea-94fc-08cdc93f0f18.JPG" />


## 서킷 브레이킹 / 장애격리

* Spring FeignClient + Hystrix 구현
* Lecture 서비스 내 PaymentService FeignClient에 적용

- Hystrix 설정

```
# application.yml

feign:
  hystrix:
    enabled: true

hystrix:
  command:
    # 전역설정
    default:
      execution.isolation.thread.timeoutInMilliseconds: 610
```

- 서비스 지연 설정

```
//circuit test
try {
    Thread.currentThread().sleep((long) (400 + Math.random() * 220));
} catch (InterruptedException e) { }
```

- 부하 테스트 수행

```
$ siege -c100 -t60S -r10 --content-type "application/json" 'http://localhost:8082/lectures/ POST {"courseId":1, "studentId":1}'
```

- 부하 테스트 결과

```
Hibernate: 
    call next value for hibernate_sequence
Hibernate: 
    insert 
    into
        lecture
        (canceled, completed, course_id, paid, status, student_id, lecture_id) 
    values
        (?, ?, ?, ?, ?, ?, ?)
[strix-payment-6] o.s.c.openfeign.support.SpringEncoder    : Writing [edu.intensive.external.Payment@660e4680] > 정상 수행
Circuit breaker has been opened. Fallback returned instead.
Circuit breaker has been opened. Fallback returned instead.
Circuit breaker has been opened. Fallback returned instead.
[strix-payment-5] o.s.c.openfeign.support.SpringEncoder    : Writing [edu.intensive.external.Payment@30fac0da]
[strix-payment-7] o.s.c.openfeign.support.SpringEncoder    : Writing [edu.intensive.external.Payment@14dfcb68]
[strix-payment-2] o.s.c.openfeign.support.SpringEncoder    : Writing [edu.intensive.external.Payment@e03eab]
```

```
defaulting to time-based testing: 60 seconds

{	"transactions":			         901,
	"availability":			      100.00,
	"elapsed_time":			       59.65,
	"data_transferred":		        0.25,
	"response_time":		        5.82,
	"transaction_rate":		       15.10,
	"throughput":			        0.00,
	"concurrency":			       87.86,
	"successful_transactions":	         901,
	"failed_transactions":		           0,
	"longest_transaction":		       24.01,
	"shortest_transaction":		        0.02
}
```


### 오토스케일 아웃

- 현재 상태 확인

```
Non-terminated Pods:          (6 in total)
Namespace                   Name                       CPU Requests  CPU Limits  Memory Requests  Memory Limits  AGE
---------                   ----                       ------------  ----------  ---------------  -------------  ---
default                     httpie                     0 (0%)        0 (0%)      0 (0%)           0 (0%)         18h
default                     lecture-879d9f5fb-vs2gm    0 (0%)        0 (0%)      0 (0%)           0 (0%)         11h
kafka                       my-kafka-0                 0 (0%)        0 (0%)      0 (0%)           0 (0%)         18h
kafka                       my-kafka-zookeeper-1       0 (0%)        0 (0%)      0 (0%)           0 (0%)         26h
kube-system                 aws-node-l5xq4             10m (0%)      0 (0%)      0 (0%)           0 (0%)         43h
kube-system                 kube-proxy-svkl4           100m (5%)     0 (0%)      0 (0%)           0 (0%)         43h
```

- 오토스케일 설정
```
kubectl autoscale deploy pay --min=1 --max=3 --cpu-percent=1
```

- 부하 수행

```
siege -c10 -t10S -r10 --content-type "application/json" 'http://a12cb0b3753c14281ba577ae539765a2-2145726089.ap-northeast-2.elb.amazonaws.com:8080/lectures POST {"courseId":5, "studentId":5}' -v
```

- 모니터링

```
kubectl get deploy lecture -w
```

- 스케일 아웃 확인

```
NAME      READY   UP-TO-DATE   AVAILABLE   AGE
lecture   1/1     1            1           18h
```

```
HTTP/1.1 201     0.05 secs:     424 bytes ==> POST http://a12cb0b3753c14281ba577ae539765a2-2145726089.ap-northeast-2.elb.amazonaws.com:8080/lectures
[error] unable to set close control sock.c:141: Invalid argument
HTTP/1.1 201     0.08 secs:     424 bytes ==> POST http://a12cb0b3753c14281ba577ae539765a2-2145726089.ap-northeast-2.elb.amazonaws.com:8080/lectures
[error] unable to set close control sock.c:141: Invalid argument
HTTP/1.1 201     0.11 secs:     424 bytes ==> POST http://a12cb0b3753c14281ba577ae539765a2-2145726089.ap-northeast-2.elb.amazonaws.com:8080/lectures
[error] unable to set close control sock.c:141: Invalid argument

Lifting the server siege...
Transactions:                   1116 hits
Availability:                 100.00 %
Elapsed time:                   9.56 secs
Data transferred:               0.45 MB
Response time:                  0.08 secs
Transaction rate:             116.74 trans/sec
Throughput:                     0.05 MB/sec
Concurrency:                    9.87
Successful transactions:        1116
Failed transactions:               0
Longest transaction:            0.27
Shortest transaction:           0.03
```


## 무정지 재배포

<img src="https://user-images.githubusercontent.com/62231786/84969468-bd075e00-b153-11ea-8c28-594aceb378c7.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969469-bd9ff480-b153-11ea-9180-a4f36cf63938.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969470-bd9ff480-b153-11ea-9159-d5f37bb10174.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969460-bbd63100-b153-11ea-9368-67a78fcd3308.JPG" />
