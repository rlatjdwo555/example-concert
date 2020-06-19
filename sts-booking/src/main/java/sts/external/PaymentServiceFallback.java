package sts.external;

import org.springframework.stereotype.Component;
import sts.Payment;

@Component
public class PaymentServiceFallback implements PaymentService {

    @Override
    public void requestPayment(Payment payment) {
        System.out.println("Circuit breaker has been opened. Fallback returned instead.");
    }

}

