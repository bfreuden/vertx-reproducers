package org.bfreuden;

import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.ext.web.FileUpload;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class Main {

    public static void main( String[] args ) throws IOException {
        Vertx vertx = Vertx.vertx();
        HttpServer server = vertx.createHttpServer();
        String content = FileUtils.readFileToString(new File("target/docs/swagger-initializer.js"), "UTF-8");
        content = content.replace("https://petstore.swagger.io/v2/swagger.json", "/openapi/demo.yaml");
        FileUtils.write(new File("target/docs/swagger-initializer.js"), content, "UTF-8");
        RouterBuilder.create(vertx, "src/main/resources/openapi/demo.yaml")
                .onSuccess(routerBuilder -> {
                    try {
                        routerBuilder.rootHandler(BodyHandler.create());
                        routerBuilder
                                .operation("upload")
                                .handler(Main::handleFileUpload);
                        Router apiRouter = routerBuilder.createRouter();
                        Router router = Router.router(vertx);
                        router.route("/docs/*").handler(StaticHandler.create("target/docs").setCachingEnabled(false));
                        router.route("/openapi/*").handler(StaticHandler.create("src/main/resources/openapi").setCachingEnabled(false));
                        router.route("/*").subRouter(apiRouter);
                        router.errorHandler(400, Main::handle400error);
                        int port = 8888;
                        server.requestHandler(router).listen(port);
                        System.out.println("Swagger UI on http://localhost:" + port +"/docs");

                    } catch (Throwable t) {
                        System.err.println("Unable start server");
                        t.printStackTrace();
                    }
                })
                .onFailure(err -> {
                    System.err.println("Unable to read the openapi spec");
                    err.printStackTrace();
                });

    }

    private static void handle400error(RoutingContext context) {
        deleteUploads(context);
        context.failure().printStackTrace();
        context.response().setStatusCode(400).setStatusMessage(context.failure().getMessage()).send();
    }

    private static void deleteUploads(RoutingContext context) {
        List<FileUpload> fileUploads = context.fileUploads();
        for (FileUpload fileUpload : fileUploads) {
            context.vertx().fileSystem().deleteBlocking(fileUpload.uploadedFileName());
        }
    }

    private static void handleFileUpload(RoutingContext context) {
        deleteUploads(context);
        context.response().setStatusCode(204).send();
    }

}
