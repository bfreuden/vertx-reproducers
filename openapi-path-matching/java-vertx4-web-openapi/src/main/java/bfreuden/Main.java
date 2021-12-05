package bfreuden;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.openapi.RouterBuilder;

public class Main {

    public static void main( String[] args ) {
        Vertx vertx = Vertx.vertx();
        String spec = "../openapi.yaml";
//        String spec = "openapi-path-matching/openapi.yaml";
        int port = 8084;
        RouterBuilder.create(vertx, spec, ar -> {
            if (ar.succeeded()) {
                try {
                    RouterBuilder builder = ar.result();
                    builder.operation("searchTerms").handler(Main::searchTerms);
                    builder.operation("getLexicon").handler(Main::getLexicon);
                    builder.operation("searchProjectTerms").handler(Main::searchTerms);
                    builder.operation("getProjectLexicon").handler(Main::getLexicon);
                    Router router = builder.createRouter();
                    vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(port);
                    System.out.println("Ready to serve on http://localhost:" + port);
                    System.out.println("Try:");
                    System.out.println("");
                    System.out.println("curl localhost:" + port + "/lexicons/pets");
                    System.out.println("curl -XPOST localhost:" + port + "/lexicons/_search");
                    System.out.println("curl localhost:" + port + "/projects/mine/lexicons/pets");
                    System.out.println("curl -XPOST localhost:" + port + "/projects/mine/lexicons/_search");

                } catch (Exception ex) {
                    ex.printStackTrace();
                    vertx.close();
                }
            } else {
                ar.cause().printStackTrace();
                vertx.close();
            }
        });
    }

    public static void getLexicon(RoutingContext rc) {
        rc.response().end(
                new JsonObject()
                        .put("name", rc.pathParam("lexiconName"))
                        .encodePrettily());
    }

    public static void searchTerms(RoutingContext rc) {
        rc.response().end(
                new JsonObject()
                .put("hits",
                    new JsonArray()
                            .add(new JsonObject()
                                    .put("name", "Wolfie"))).encodePrettily());
    }

}
