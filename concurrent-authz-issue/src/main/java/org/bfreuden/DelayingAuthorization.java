package org.bfreuden;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AuthorizationProvider;

// a provider that just adds some random delay before calling a real authorization provider.
// serves to mimic a remote (mongodb) authorization provider.
public class DelayingAuthorization implements AuthorizationProvider {

    private final Vertx vertx;
    private final AuthorizationProvider provider;

    public DelayingAuthorization(Vertx vertx, AuthorizationProvider provider) {
        this.vertx = vertx;
        this.provider = provider;
    }

    @Override
    public String getId() {
        return provider.getId();
    }

    @Override
    public void getAuthorizations(User user, Handler<AsyncResult<Void>> handler) {
        vertx.setTimer(15L + Math.round(5L * Math.random()), h -> {
            try {
                provider.getAuthorizations(user, handler);
            } catch (Throwable t) {
                // yes, it happens sometimes (see out.txt stack traces)
                handler.handle(Future.failedFuture(t));
            }
        });
    }

}
