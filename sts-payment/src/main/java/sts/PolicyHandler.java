package sts;

import sts.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PolicyHandler{
    @Autowired
    PaymentRepository paymentRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingCanceled(@Payload BookingCanceled bookingCanceled){

        if(bookingCanceled.isMe()){
            if (bookingCanceled.isMe()) {
                Long bookingId = bookingCanceled.getId();
                List<Payment> payments = paymentRepository.findByBookingId(bookingId);
                for (Payment payment: payments) {
                    paymentRepository.delete(payment);
                }
            }
        }
    }

}
