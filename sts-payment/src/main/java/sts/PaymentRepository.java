package sts;

import org.springframework.data.repository.PagingAndSortingRepository;

import java.util.List;

public interface PaymentRepository extends PagingAndSortingRepository<Payment, Long>{

    List<Payment> findByBookingId(Long bookingId);
}