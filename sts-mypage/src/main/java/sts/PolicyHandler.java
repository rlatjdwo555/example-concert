package sts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import sts.config.kafka.KafkaProcessor;

@Service
public class PolicyHandler{
	@Autowired
    BookingHistoryRepository bookingHistoryRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingRequested(@Payload BookingRequested bookingRequested){
    	BookingHistory bookingHistory = new BookingHistory();
    	bookingHistory.setBookingId(bookingRequested.getId());
    	bookingHistory.setConcertId(bookingRequested.getConcertId());
    	bookingHistory.setUserId(bookingRequested.getUserId());
    	bookingHistory.setStatus(BookingStatus.BookingRequested.name());
    	
        if (bookingRequested.isMe()) {
        	bookingHistoryRepository.save(bookingHistory);
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingApproved(@Payload BookingApproved bookingApproved){

        if (bookingApproved.isMe()) {
            bookingHistoryRepository.findByBookingId(bookingApproved.getId())
	            .ifPresent(
	            		bookingHistory -> {
	            			bookingHistory.setStatus(BookingStatus.BookingApproved.name());
	            			bookingHistoryRepository.save(bookingHistory);
	                    }
	            );
        }
    }
    
    @StreamListener(KafkaProcessor.INPUT)
    public void bookingCanceled(@Payload BookingCanceled bookingCanceled){

        if (bookingCanceled.isMe()) {
            bookingHistoryRepository.findByBookingId(bookingCanceled.getId())
	            .ifPresent(
	            		bookingHistory -> {
	            			bookingHistory.setStatus(BookingStatus.BookingCanceled.name());
	            			bookingHistoryRepository.save(bookingHistory);
	                    }
	            );
        }
    }
    
    @StreamListener(KafkaProcessor.INPUT)
    public void paymentRequested(@Payload PaymentRequested paymentRequested){
    	if (paymentRequested.isMe()) {
            bookingHistoryRepository.findByBookingId(paymentRequested.getBookingId())
	            .ifPresent(
	            		bookingHistory -> {
	            			bookingHistory.setStatus(PaymentStatus.PaymentRequested.name());
	            			bookingHistoryRepository.save(bookingHistory);
	                    }
	            );
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingApproved(@Payload PaymentApproved paymentApproved){

        if (paymentApproved.isMe()) {
            bookingHistoryRepository.findByBookingId(paymentApproved.getBookingId())
	            .ifPresent(
	            		bookingHistory -> {
	            			bookingHistory.setStatus(PaymentStatus.PaymentApproved.name());
	            			bookingHistoryRepository.save(bookingHistory);
	                    }
	            );
        }
    }
    
    @StreamListener(KafkaProcessor.INPUT)
    public void bookingCanceled(@Payload PaymentCanceled paymentCanceled){

        if (paymentCanceled.isMe()) {
            bookingHistoryRepository.findByBookingId(paymentCanceled.getBookingId())
	            .ifPresent(
	            		bookingHistory -> {
	            			bookingHistory.setStatus(PaymentStatus.PaymentCanceled.name());
	            			bookingHistoryRepository.save(bookingHistory);
	                    }
	            );
        }
    }
}
