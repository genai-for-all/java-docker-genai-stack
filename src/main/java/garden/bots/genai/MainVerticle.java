package garden.bots.genai;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.input.PromptTemplate;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.output.Response;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.StaticHandler;

import java.util.*;

import static dev.langchain4j.data.message.SystemMessage.systemMessage;

public class MainVerticle extends AbstractVerticle {

  @Override
  public void start(Promise<Void> startPromise) throws Exception {

    var llmBaseUrl = Optional.ofNullable(System.getenv("OLLAMA_BASE_URL")).orElse("http://localhost:11434");
    var modelName = Optional.ofNullable(System.getenv("LLM")).orElse("deepseek-coder");

    var staticPath = Optional.ofNullable(System.getenv("STATIC_PATH")).orElse("/*");
    var httpPort = Optional.ofNullable(System.getenv("HTTP_PORT")).orElse("8888");

    //https://docs.langchain4j.dev/apidocs/dev/langchain4j/model/ollama/OllamaStreamingChatModel.html
    StreamingChatLanguageModel streamingModel = OllamaStreamingChatModel.builder()
      .baseUrl(llmBaseUrl)
      .modelName(modelName).temperature(0.0).repeatPenalty(1.0)
      .build();

    SystemMessage systemInstructions = systemMessage("""
      You are an expert in computer programming.
      Please make friendly answer for the noobs.
      Add source code examples if you can.
      """);

    PromptTemplate humanPromptTemplate = PromptTemplate.from("""
      I need a clear explanation regarding my {{question}}.
      And, please, be structured.
      """);

    Router router = Router.router(vertx);

    router.route().handler(BodyHandler.create());
    // Serving static resources
    var staticHandler = StaticHandler.create();
    staticHandler.setCachingEnabled(false);
    router.route(staticPath).handler(staticHandler);

    router.post("/prompt").handler(ctx -> {

      var question = ctx.body().asJsonObject().getString("question");

      Map<String, Object> variables  = new HashMap<String, Object>() {{
        put("question", question);
      }};

      var humanMessage = humanPromptTemplate.apply(variables).toUserMessage();

      List<ChatMessage> messages = new ArrayList<>();
      messages.add(systemInstructions);
      messages.add(humanMessage);

      HttpServerResponse response = ctx.response();

      response
        .putHeader("Content-Type", "application/octet-stream")
        .setChunked(true);

      streamingModel.generate(messages, new StreamingResponseHandler<AiMessage>() {
        @Override
        public void onNext(String token) {
          System.out.println("New token: '" + token + "'");
          response.write(token);
        }

        @Override
        public void onComplete(Response<AiMessage> modelResponse) {
          System.out.println("Streaming completed: " + modelResponse);
          response.end();

        }

        @Override
        public void onError(Throwable throwable) {
          throwable.printStackTrace();
        }
      });

    });


    // Create an HTTP server
    var server = vertx.createHttpServer();

    //! Start the HTTP server
    server.requestHandler(router).listen(Integer.parseInt(httpPort), http -> {
      if (http.succeeded()) {
        startPromise.complete();
        System.out.println("GenAI Vert-x server started on port " + httpPort);
      } else {
        startPromise.fail(http.cause());
      }
    });

  }
}
