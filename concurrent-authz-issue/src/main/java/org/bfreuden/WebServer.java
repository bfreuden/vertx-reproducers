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


// a web server with:
// - a session handler
// - a basic auth handler
// - two other handlers:
//    - /public that does not perform any authz check (just authn)
//    - /protected that is performing some authz operations
public class WebServer extends AbstractVerticle {

    private AuthorizationProvider authz;
    private static long loginTime;

    public void start(Promise<Void> startPromise) {
        try {
            Router router = Router.router(vertx);
            // one session store per web server thread (is it ok?)
            LocalSessionStore store = LocalSessionStore.create(vertx);
            SessionHandler sessionHandler = SessionHandler.create(store);
            // one authorization provider per web server thread (is it ok?)
            PropertyFileAuthentication auth = PropertyFileAuthentication.create(vertx, "src/main/vertx-users.properties");
            authz = new DelayingAuthorization(vertx, PropertyFileAuthorization.create(vertx, "src/main/vertx-users.properties"));
            // add session handler to all routes
            router.route().handler(sessionHandler);
            // add basic auth to all routes
            router.route().handler(BasicAuthHandler.create(auth));
            // route not performing any authz check
            router.get("/public").handler(this::publicPageHandler);
            // route performing authz checks that will be hammered by the test program
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
        // just store the login time to make sure the following parallel requests will run almost at the same time
        loginTime = System.currentTimeMillis();
    }

    private void protectedPageHandler(RoutingContext routingContext) {
        // try to make sure all parallel requests will run almost at the same time.
        // if you comment it out, you are less likely to get 500 errors (due to ConcurrentModificationException)
        // but you will still get 403 errors quite frequently (user having no permissions when he should have).
        vertx.setTimer(System.currentTimeMillis() - loginTime + 1000, h -> {
            User user = routingContext.user();
            Promise<Void> authorizationReady = Promise.promise();
            try {
                // first clear authorizations
                // in a real program the condition will be more clever (we don't want to clear at each request)
                // but the purpose is to demonstrate that it causes problems.
                // the test is running fine if you comment it.
                // since all vertx authorization providers are cumulative (they are adding permissions to the set),
                // if your application needs to remove permission, then you need to clear authorizations before
                if (!user.authorizations().getProviderIds().isEmpty()) {
                    user.authorizations().clear();
                }
                // get authorizations only if they are empty (that's what a real program should do?)
                if (user.authorizations().getProviderIds().isEmpty()) {
                    authz.getAuthorizations(user)
                            .onSuccess(v-> {
                                // the OK scenario
                                authorizationReady.complete();
                            })
                            .onFailure(t-> {
                                // does not happen AFAIK
                                replyError(routingContext, t);
                            })
                    ;
                } else {
                    // just in case you want to comment user.authorizations().clear();
                    authorizationReady.complete();
                }

            } catch (Throwable t) {
                // yes, it happens sometimes (see out.txt stack traces)
                replyError(routingContext, t);
            }
            // once authorizations are ready, use them
            authorizationReady.future().onComplete(v -> {
                try {
                    // try to match a permission: this can throw an error sometimes (see out.txt stack traces)
                    if (PermissionBasedAuthorization.create("do:stuff").match(user)) {
                        Authorizations authorizations = user.authorizations();
                        // this iteration is also likely to raise ConcurrentModificationException on authorizations
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
                    // yes, it happens sometimes (see out.txt stack traces)
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