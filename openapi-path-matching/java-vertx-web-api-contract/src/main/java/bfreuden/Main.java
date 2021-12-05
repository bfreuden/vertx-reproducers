package bfreuden;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.api.contract.openapi3.OpenAPI3RouterFactory;

public class Main {

    public static void main( String[] args ) {
        Vertx vertx = Vertx.vertx();
        String spec = "../openapi.yaml";
//        String spec = "openapi-path-matching/openapi.yaml";
        int port = 8083;
        OpenAPI3RouterFactory.create(vertx, spec, ar -> {
            if (ar.succeeded()) {
                try {
                    OpenAPI3RouterFactory factory = ar.result();
                    factory.addHandlerByOperationId("searchTerms", Main::searchTerms);
                    factory.addHandlerByOperationId("getLexicon", Main::getLexicon);
                    factory.addHandlerByOperationId("searchProjectTerms", Main::searchTerms);
                    factory.addHandlerByOperationId("getProjectLexicon", Main::getLexicon);
                    Router router = factory.getRouter();
                    vertx.createHttpServer()
                            .requestHandler(router)
                            .listen(port);
                    System.out.println("Ready to serve on http://localhost:" + port);
                    System.out.println("Try:");
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
