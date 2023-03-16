package org.bfreuden;

import com.ibm.asyncutil.locks.AsyncLock;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authentication.AuthenticationProvider;

public class UserWithLockAuthenticationProvider implements AuthenticationProvider {

    private final AuthenticationProvider wrapped;
    public static final String LOCK_ATT_NAME = "__LOCK__";
    public UserWithLockAuthenticationProvider(AuthenticationProvider wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void authenticate(JsonObject credentials, Handler<AsyncResult<User>> resultHandler) {
        wrapped.authenticate(credentials, ar -> {
           if (ar.succeeded()) {
               User result = ar.result();
               result.attributes().getMap().put(LOCK_ATT_NAME, AsyncLock.create());
               resultHandler.handle(Future.succeededFuture(result));
           } else {
               resultHandler.handle(Future.failedFuture(ar.cause()));
           }
        });
    }
}
