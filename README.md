# 콘서트 예매 시스템

# Table of contents

- [서비스 시나리오](#서비스-시나리오)
- [분석/설계](#분석설계)
- [구현](#구현)
  - [DDD 의 적용](#ddd-의-적용)
  - [Gateway 적용](#Gateway-적용)
  - [폴리글랏 퍼시스턴스](#폴리글랏-퍼시스턴스)
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

<img src="https://user-images.githubusercontent.com/62231786/85047444-ce408100-b1cc-11ea-805f-1c2557c986c5.png"/>

```
# 도메인 서열
- Core : Booking
- Supporting : Concert, User
- General : Payment
```


## 헥사고날 아키텍처 다이어그램 도출

* CQRS 를 위한 Mypage 서비스만 DB를 구분하여 적용
    
![image](https://user-images.githubusercontent.com/62231786/85047293-92a5b700-b1cc-11ea-9798-dfd58f79d993.png)


# 구현

## DDD 의 적용

분석/설계 단계에서 도출된 MSA는 총 5개로 아래와 같다.
* MyPage 는 CQRS 를 위한 서비스

| MSA | 기능 | port | 조회 API |
|---|:---:|:---:|:---:|
| Concert | 콘서트 관리 | 8081 | http://localhost:8081/cooncerts |
| Booking | 예약 관리 | 8082 | http://localhost:8082/bookings |
| Payment | 결제 관리 | 8083 | http://localhost:8083/payments |
| User | 사용자 관리 | 8084 | http://localhost:8084/users |
| MyPage | 콘서트 예약내역 관리 | 8086 | http://localhost:8085/myPage |


## Gateway 적용

```
spring:
  profiles: default
  cloud:
    gateway:
      routes:
        - id: concert
          uri: http://localhost:8081
          predicates:
            - Path=/concerts/** 
        - id: booking
          uri: http://localhost:8082
          predicates:
            - Path=/bookings/** 
        - id: payment
          uri: http://localhost:8083
          predicates:
            - Path=/payments/** 
        - id: user
          uri: http://localhost:8084
          predicates:
            - Path=/users/** 
        - id: mypage
          uri: http://localhost:8085
          predicates:
            - Path=/bookingHistories/**
```


## 폴리글랏 퍼시스턴스

CQRS 를 위한 Mypage 서비스만 DB를 구분하여 적용함. 인메모리 DB인 hsqldb 사용.

```
<!-- 
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>runtime</scope>
		</dependency>
 -->
		<dependency>
		    <groupId>org.hsqldb</groupId>
		    <artifactId>hsqldb</artifactId>
		    <version>2.4.0</version>
		    <scope>runtime</scope>
		</dependency>
```


## 동기식 호출 과 Fallback 처리

예약 > 결제 간의 호출은 동기식 일관성을 유지하는 트랜잭션으로 처리

- FeignClient 서비스 구현

```
# PaymentService.java

@FeignClient(name="payment", url="${feign.payment.url}", fallback = PaymentServiceFallback.class)
public interface PaymentService {
    @PostMapping(path="/payments")
    public void requestPayment(Payment payment);
}
```


- 동기식 호출

```
# Booking.java

@PostPersist
public void onPostPersist(){
    BookingRequested bookingRequested = new BookingRequested();
    BeanUtils.copyProperties(this, bookingRequested);
    bookingRequested.setStatus(BookingStatus.BookingRequested.name());
    bookingRequested.publishAfterCommit();

    Payment payment = new Payment();
    payment.setBookingId(this.id);

    Application.applicationContext.getBean(PaymentService.class).requestPayment(payment);
}
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
# Booking.java

@PostUpdate
public void onPostUpdate(){
    if (BookingStatus.BookingApproved.name().equals(this.getStatus())) {
        BookingApproved bookingApproved = new BookingApproved();
        BeanUtils.copyProperties(this, bookingApproved);
        bookingApproved.publishAfterCommit();
    }
}
```

- 비동기식 수신 구현

```
# PolicyHandler.java

@StreamListener(KafkaProcessor.INPUT)
public void paymentApproved(@Payload PaymentApproved paymentApproved){
    if(paymentApproved.isMe()){
	bookingRepository.findById(paymentApproved.getBookingId())
	    .ifPresent(
			booking -> {
				booking.setStatus(BookingStatus.BookingApproved.name());;
				bookingRepository.save(booking);
		    }
	    )
	;
    }
}
```


# 운영

## CI/CD 설정

- CodeBuild 기반으로 파이프라인 구성

<img src="https://user-images.githubusercontent.com/62231786/84913102-1c825100-b0f5-11ea-9b92-5119abc18415.png"/>

- Git Hook 연경

<img src="https://user-images.githubusercontent.com/62231786/84975759-38bbd780-b161-11ea-94fc-08cdc93f0f18.JPG" />


## 서킷 브레이킹 / 장애격리

* Spring FeignClient + Hystrix 구현
* Booking 서비스 내 PaymentService FeignClient에 적용

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
$ siege -c100 -t60S -r10 --content-type "application/json" 'http://localhost:8082/bookings/ POST {"concertId":1, "userId":1, "qty":5}'
```

- 부하 테스트 결과

```
2020-06-19 01:54:52.576[0;39m [32mDEBUG[0;39m [35m6600[0;39m [2m---[0;39m [2m[container-0-C-1][0;39m [36mo.s.c.s.m.DirectWithAttributesChannel   [0;39m [2m:[0;39m preSend on channel 'event-in', message: GenericMessage [payload=byte[142], headers={kafka_offset=4013, scst_nativeHeadersPresent=true, kafka_consumer=org.apache.kafka.clients.consumer.KafkaConsumer@5775c5aa, deliveryAttempt=1, kafka_timestampType=CREATE_TIME, kafka_receivedMessageKey=null, kafka_receivedPartitionId=0, contentType=application/json, kafka_receivedTopic=sts, kafka_receivedTimestamp=1592499287785}]
Circuit breaker has been opened. Fallback returned instead.
Circuit breaker has been opened. Fallback returned instead.
[2m2020-06-19 01:54:52.576[0;39m [32mDEBUG[0;39m [35m6600[0;39m [2m---[0;39m [2m[o-8082-exec-153][0;39m [36mo.s.c.s.m.DirectWithAttributesChannel   [0;39m [2m:[0;39m postSend (sent=true) on channel 'event-out', message: GenericMessage [payload=byte[142], headers={contentType=application/json, id=cbdf4d07-547d-5dbe-80a1-659a0e00b607, timestamp=1592499291969}]
[2m2020-06-19 01:54:52.576[0;39m [32mDEBUG[0;39m [35m6600[0;39m [2m---[0;39m [2m[o-8082-exec-166][0;39m [36mo.s.c.s.m.DirectWithAttributesChannel   [0;39m [2m:[0;39m postSend (sent=true) on channel 'event-out', message: GenericMessage [payload=byte[142], headers={contentType=application/json, id=3a646994-497f-717a-cb13-443133007248, timestamp=1592499291969}]
```

```
defaulting to time-based testing: 30 seconds

{	"transactions":			         447,
	"availability":			      100.00,
	"elapsed_time":			       29.92,
	"data_transferred":		        0.10,
	"response_time":		        6.11,
	"transaction_rate":		       14.94,
	"throughput":			        0.00,
	"concurrency":			       91.21,
	"successful_transactions":	         447,
	"failed_transactions":		           0,
	"longest_transaction":		       17.07,
	"shortest_transaction":		        0.00
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
kubectl autoscale deploy booking --min=1 --max=3 --cpu-percent=1
```

- 부하 수행

```
siege -c100 -t60S -r10 --content-type "application/json" 'http://aa8dc72fe9cbb4ba0ba62c5720326102-1685876144.ap-northeast-2.elb.amazonaws.com:8080/bookings/ POST {"concertId":1, "userId":1, "qty":5}' -v
```

- 모니터링

```
kubectl get deploy booking -w
```

- 스케일 아웃 확인

```
NAME      READY   UP-TO-DATE   AVAILABLE   AGE
booking   1/1     1            1           5h9m
```

```
defaulting to time-based testing: 60 seconds

{	"transactions":			        6316,
	"availability":			      100.00,
	"elapsed_time":			       60.00,
	"data_transferred":		        1.43,
	"response_time":		        0.94,
	"transaction_rate":		      105.27,
	"throughput":			        0.02,
	"concurrency":			       99.46,
	"successful_transactions":	        6316,
	"failed_transactions":		           0,
	"longest_transaction":		        6.22,
	"shortest_transaction":		        0.05
}
```


## 무정지 재배포

<img src="https://user-images.githubusercontent.com/62231786/84969468-bd075e00-b153-11ea-8c28-594aceb378c7.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969469-bd9ff480-b153-11ea-9180-a4f36cf63938.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969470-bd9ff480-b153-11ea-9159-d5f37bb10174.JPG" />
<img src="https://user-images.githubusercontent.com/62231786/84969460-bbd63100-b153-11ea-9368-67a78fcd3308.JPG" />
