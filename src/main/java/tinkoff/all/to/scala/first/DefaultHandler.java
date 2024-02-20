package tinkoff.all.to.scala.first;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class DefaultHandler implements Handler {

    private static final int HANDLER_TIMEOUT_MS = 15000;
    private final Instant startTime = Instant.now();
    private final AtomicReference<Instant> lastRequestTime = new AtomicReference<>(null);

    @Override
    public ApplicationStatusResponse performOperation(String id) {

        Client client = new DefaultClient();
        AtomicReference<Response> response1 = new AtomicReference<>(null);
        AtomicReference<Response> response2 = new AtomicReference<>(null);
        AtomicInteger clientRetries = new AtomicInteger(-2);
        ApplicationStatusResponse result = null;


        Thread thread1 = new Thread(() -> {
            while (response1.get() == null) {
                lastRequestTime.set(Instant.now());
                clientRetries.getAndIncrement();
                var response = client.getApplicationStatus1(id);
                response1.set(checkResponse(response));            }
        });
        thread1.setDaemon(true);
        thread1.start();

        Thread thread2 = new Thread(() -> {
            while (response2.get() == null) {
                lastRequestTime.set(Instant.now());
                clientRetries.getAndIncrement();
                var response = client.getApplicationStatus2(id);
                response2.set(checkResponse(response));
            }
        });
        thread2.setDaemon(true);
        thread2.start();

        long expire = System.currentTimeMillis() + HANDLER_TIMEOUT_MS;
        boolean responded = false;
        while (System.currentTimeMillis() < expire && !responded) {
            var response = response1.get() != null ? response1.get() : response2.get();
            if (response != null ) {
                responded = true;
                if (response.getClass().equals(Response.Success.class)) {
                    result = mapSuccess((Response.Success) response);
                } else if (response.getClass().equals(Response.Failure.class)) {
                    result = mapFailure(clientRetries.get());
                }
            }
        }

        if (!responded) {
            return mapFailure(clientRetries.get());
        }

        return result;
    }

    private ApplicationStatusResponse mapSuccess(Response.Success response) {
        return new ApplicationStatusResponse.Success(response.applicationStatus(), response.applicationId());
    }

    private ApplicationStatusResponse mapFailure(int retriesCount) {
        if (lastRequestTime.get() != null) {
            return new ApplicationStatusResponse.Failure(null, retriesCount);
        } else {
            return new ApplicationStatusResponse.Failure(Duration.between(startTime, lastRequestTime.get()), retriesCount);
        }
    }

    private Response checkResponse(Response response) {
        if (response.getClass().equals(Response.RetryAfter.class)) {
            try {
                Thread.sleep(((Response.RetryAfter) response).delay().toMillis());
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            return null;
        } else {
            return response;
        }
    }
}
