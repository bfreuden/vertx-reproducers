package org.bfreuden;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.AuthorizationProvider;

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
        vertx.setTimer(15l + Math.round(Math.random()*5), h -> provider.getAuthorizations(user, handler));
    }

}
