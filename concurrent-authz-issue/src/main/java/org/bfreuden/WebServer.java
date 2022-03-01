package org.bfreuden;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.ext.auth.User;
import io.vertx.ext.auth.authorization.Authorization;
import io.vertx.ext.auth.authorization.AuthorizationProvider;
import io.vertx.ext.auth.authorization.Authorizations;
import io.vertx.ext.auth.authorization.PermissionBasedAuthorization;
import io.vertx.ext.auth.properties.PropertyFileAuthentication;
import io.vertx.ext.auth.properties.PropertyFileAuthorization;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BasicAuthHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Set;

public class WebServer extends AbstractVerticle {

    private AuthorizationProvider authz;
    private static long loginTime;

    public void start(Promise<Void> startPromise) {
        try {
            Router router = Router.router(vertx);
            LocalSessionStore store = LocalSessionStore.create(vertx);
            SessionHandler sessionHandler = SessionHandler.create(store);
            PropertyFileAuthentication auth = PropertyFileAuthentication.create(vertx, "src/main/vertx-users.properties");
            authz = new DelayingAuthorization(vertx, PropertyFileAuthorization.create(vertx, "src/main/vertx-users.properties"));
            router.route().handler(sessionHandler);
            router.route().handler(BasicAuthHandler.create(auth));
            router.get("/public").handler(this::publicPageHandler);
            router.get("/protected").handler(this::protectedPageHandler);
            int port = App.PORT;
            vertx.createHttpServer()
                    .requestHandler(router)
                    .listen(port)
                    .onSuccess(v -> {
                        System.out.println("server started in port " + port);
                        startPromise.complete();
                    })
                    .onFailure(startPromise::fail);

        } catch (Throwable t) {
            startPromise.fail(t);
        }
    }

    private void publicPageHandler(RoutingContext routingContext) {
        replySuccess(routingContext, "<h1>public page</h1>");
        loginTime = System.currentTimeMillis();
    }

    private void protectedPageHandler(RoutingContext routingContext) {
        vertx.setTimer(System.currentTimeMillis() - loginTime + 1000, h -> {
            User user = routingContext.user();
            Promise<Void> authorizationReady = Promise.promise();
            try {
                if (!user.authorizations().getProviderIds().isEmpty()) {
                    user.authorizations().clear();
                }
                if (user.authorizations().getProviderIds().isEmpty()) {
                    authz.getAuthorizations(user)
                            .onSuccess(v-> {
                                authorizationReady.complete();
                            })
                            .onFailure(t-> {
                                replyError(routingContext, t);
                            })
                    ;
                } else {
                    authorizationReady.complete();
                }

            } catch (Throwable t) {
                replyError(routingContext, t);
            }
            authorizationReady.future().onComplete(v -> {
                try {
                    if (PermissionBasedAuthorization.create("do:stuff").match(user)) {
                        Authorizations authorizations = user.authorizations();
                        for (String providerId : authorizations.getProviderIds()) {
                            Set<Authorization> authorizationSet = authorizations.get(providerId);
                            StringBuilder builder = new StringBuilder();
                            for (Authorization authorization : authorizationSet) {
                                builder.append(authorization.toString());
                            }
                        }
                        replySuccess(routingContext, "<h1>protected page</h1>");
                    } else {
                        replyNotAuthorized(routingContext, "<h1>not authorized</h1>");
                    }
                } catch (Throwable t) {
                    replyError(routingContext, t);
                }
            });
        });
    }

    private void replyNotAuthorized(RoutingContext routingContext, String body) {
        routingContext.response().setStatusCode(403).putHeader("content-type", "text/html").end(body);
    }

    private void replySuccess(RoutingContext routingContext, String body) {
        routingContext.response().setStatusCode(200).putHeader("content-type", "text/html").end(body);
    }

    private void replyError(RoutingContext routingContext, Throwable t) {
        StringWriter stringWriter = new StringWriter();
        PrintWriter printWriter = new PrintWriter(stringWriter);
        t.printStackTrace(printWriter);
        printWriter.close();
        String stack = stringWriter.toString().replace("\n", "<br>");
        routingContext.response().setStatusCode(500).putHeader("content-type", "text/html").end("<h1>" + stack +"</h1>");
    }


}