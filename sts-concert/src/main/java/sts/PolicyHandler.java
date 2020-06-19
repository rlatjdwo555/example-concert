package sts;

import sts.config.kafka.KafkaProcessor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.stream.annotation.StreamListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class PolicyHandler{
    @Autowired
    ConcertRepository concertRepository;

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingApproved(@Payload BookingApproved bookingApproved) {
        if (bookingApproved.isMe()) {
            concertRepository.findById(bookingApproved.getConcertId())
                    .ifPresent(
                            concert -> {
                                concert.setTicketQty(concert.getTicketQty() - bookingApproved.getQty());
                                concertRepository.save(concert);
                            }
                    )
            ;
        }
    }

    @StreamListener(KafkaProcessor.INPUT)
    public void bookingCanceled(@Payload BookingCanceled bookingCanceled) {
        if (bookingCanceled.isMe()) {
            concertRepository.findById(bookingCanceled.getConcertId())
                    .ifPresent(
                            concert -> {
                                concert.setTicketQty(concert.getTicketQty() + bookingCanceled.getQty());
                                concertRepository.save(concert);
                            }
                    )
            ;
        }
    }
}
