package org.bfreuden;

import io.vertx.core.impl.ConcurrentHashSet;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.WebClientOptions;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.HashSet;
import java.util.concurrent.CountDownLatch;

import static org.junit.Assert.assertEquals;

public class AppTest {

    @BeforeClass
    public static void startServer() throws InterruptedException {
        App.main(new String[0]);
    }

    Throwable err;
    String cookie;

    @Test
    public void test() throws Throwable {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        HashSet<Integer> statusCodes = new HashSet<>();
        HashSet<String> bodies = new HashSet<>();
        HashSet<Integer> only200 = new HashSet<>();
        only200.add(200);
        int concurrency = 64;
        // authenticate and get cookie
        WebClient webClient = WebClient.create(App.vertx,
                new WebClientOptions()
                        .setDefaultHost("localhost")
                        .setDefaultPort(8888)
                        .setMaxPoolSize(concurrency)
        );
        webClient.get("/public")
                .basicAuthentication("tim", "sausages")
                .send()
                .onFailure(t -> {
                    err = t;
                    countDownLatch.countDown();
                })
                .onSuccess(response -> {
                    cookie = response.getHeader("set-cookie");
                    cookie = cookie.substring(0, cookie.indexOf(";"));
                    System.out.println(cookie);
                    System.out.println(response.bodyAsString());
                    statusCodes.add(response.statusCode());
                    countDownLatch.countDown();
                });
        countDownLatch.await();
        if (err != null)
            throw err;
        assertEquals(only200, statusCodes);
        statusCodes.clear();

        // get protected endpoint: issue a lot of parallel requests
        CountDownLatch countDownLatch2 = new CountDownLatch(concurrency);
        for (int i=0 ; i<concurrency ; i++) {
            webClient.get("/protected")
                    .putHeader("Cookie", cookie)
                    .send()
                    .onFailure(t -> {
                        err = t;
                        countDownLatch2.countDown();
                    })
                    .onSuccess(response -> {
                        synchronized (statusCodes) {
                            statusCodes.add(response.statusCode());
                            bodies.add(response.bodyAsString());
                        }
                        countDownLatch2.countDown();
                    });
        }
        countDownLatch2.await();
        if (err != null)
            throw err;
        if (!only200.equals(statusCodes)) {
            for (String body : bodies) {
                System.out.println(body.replace("<br>", "\n"));
            }
        }
        assertEquals(only200, statusCodes);
    }

    @AfterClass
    public static void stopServer() {
        App.vertx.close();
    }
}
