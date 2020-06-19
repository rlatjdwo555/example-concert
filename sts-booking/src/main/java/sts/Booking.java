package sts;

import javax.persistence.*;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import sts.external.PaymentService;

import java.util.List;

@Entity
@Table(name="Booking_table")
public class Booking {

    @Id
    @GeneratedValue(strategy=GenerationType.AUTO)
    private Long id;
    private Long concertId;
    private Long userId;
    private String status;
    private Integer qty;

    @PostPersist
    public void onPostPersist(){
        BookingRequested bookingRequested = new BookingRequested();
        BeanUtils.copyProperties(this, bookingRequested);
        bookingRequested.setStatus("BookingRequested");
        bookingRequested.publishAfterCommit();

        Payment payment = new Payment();
        payment.setBookingId(this.id);

        Application.applicationContext.getBean(PaymentService.class).requestPayment(payment);
    }

    @PostUpdate
    public void onPostUpdate(){
        BookingApproved bookingApproved = new BookingApproved();
        BeanUtils.copyProperties(this, bookingApproved);
        bookingApproved.setStatus("BookingApproved");
        bookingApproved.publishAfterCommit();


    }

    @PreRemove
    public void onPreRemove(){
        BookingCanceled bookingCanceled = new BookingCanceled();
        BeanUtils.copyProperties(this, bookingCanceled);
        bookingCanceled.setStatus("BookingCanceled");
        bookingCanceled.publishAfterCommit();


    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
    public Long getConcertId() {
        return concertId;
    }

    public void setConcertId(Long concertId) {
        this.concertId = concertId;
    }
    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
    public Integer getQty() {
        return qty;
    }

    public void setQty(Integer qty) {
        this.qty = qty;
    }




}
