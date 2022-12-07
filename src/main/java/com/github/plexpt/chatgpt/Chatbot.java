package com.github.plexpt.chatgpt;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.google.gson.Gson;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import cn.hutool.http.HttpResponse;
import cn.hutool.http.HttpUtil;
import lombok.Data;
import lombok.SneakyThrows;
import okhttp3.Response;

@Data
public class Chatbot {
    private Map<String, String> config;
    private String conversationId;
    private String parentId;
    private Map<String, String> headers;

    private String conversationIdPrev;
    private String parentIdPrev;


    private final Gson gson = new Gson();

    public Chatbot(Map<String, String> config, String conversationId) {
        this.config = config;
        this.conversationId = conversationId;
        this.parentId = UUID.randomUUID().toString();
        if (config.containsKey("session_token") || (config.containsKey("email")
                && config.containsKey("password"))) {
            refreshSession();
        }
    }

    // Resets the conversation ID and parent ID
    public void resetChat() {
        this.conversationId = null;
        this.parentId = UUID.randomUUID().toString();
    }


    // Refreshes the headers -- Internal use only
    public void refreshHeaders() {
        if (!config.containsKey("Authorization")) {
            config.put("Authorization", "");
        } else if (config.get("Authorization") == null) {
            config.put("Authorization", "");
        }
        this.headers = new HashMap<String, String>() {{
            put("Host", "chat.openai.com");
            put("Accept", "text/event-stream");
            put("Authorization", "Bearer " + config.get("Authorization"));
            put("Content-Type", "application/json");
            put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1" +
                    ".15 (KHTML, like Gecko) " +
                    "Version/16.1 Safari/605.1.15");
            put("X-Openai-Assistant-App-Id", "");
            put("Connection", "close");
            put("Accept-Language", "en-US,en;q=0.9");
            put("Referer", "https://chat.openai.com/chat");
        }};

    }



    Map<String, Object> getChatStream(Map<String, Object> data) {
        String url = "https://chat.openai.com/backend-api/conversation";

        String responseBody = HttpUtil.createPost(url)
                .headerMap(headers, true)
                .body(JSON.toJSONString(data), "application/json")
                .execute()
                .body();

//        String responseBody = response.body().toString();

        System.out.println("getChatStream: " + responseBody);

        JSONObject lineData = JSON.parseObject(responseBody);
        try {

            String message =
                    lineData.getJSONObject("message").getJSONObject("content").getJSONArray(
                            "parts").getString(0);

            conversationId = (String) lineData.get("conversation_id");
            parentId = (String) ((Map<String, Object>) lineData.get("message")).get("id");

            Map<String, Object> chatData = new HashMap<>();
            chatData.put("message", message);
            chatData.put("conversation_id", conversationId);
            chatData.put("parent_id", parentId);
            return chatData;
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    // Gets the chat response as text -- Internal use only
    public Map<String, Object> getChatText(Map<String, Object> data) {

        // Create request session
        Session session = new Session();

        // set headers
        session.setHeaders(this.headers);

        // Set multiple cookies
        session.getCookies().put("__Secure-next-auth.session-token", config.get(
                "session_token"));
        session.getCookies().put("__Secure-next-auth.callback-url", "https://chat.openai.com/");

        // Set proxies
        if (config.get("proxy") != null && !config.get("proxy").equals("")) {
            Map<String, String> proxies = new HashMap<>();
            proxies.put("http", config.get("proxy"));
            proxies.put("https", config.get("proxy"));
            session.setProxies(proxies);
        }

        Response response = session.post("https://chat.openai.com/backend-api/conversation", data);

        String errorDesc = "";

        try {
            JSONObject responseObject = JSON.parseObject(response.toString());


            this.parentId = (String) responseObject.getJSONObject("message").get("id");
            this.conversationId = (String) responseObject.get("conversation_id");

            Map<String, Object> message = (Map<String, Object>) responseObject.getJSONObject(
                            "message")
                    .getJSONObject("content")
                    .getJSONArray("parts")
                    .get(0);
            Map<String, Object> result = new HashMap<>();
            result.put("message", message);
            result.put("conversation_id", this.conversationId);
            result.put("parent_id", this.parentId);
            return result;

        } catch (Exception e) {
            e.printStackTrace();
            try {
                // Get the title text
                System.out.println(response.toString());

                String titleText = Pattern.compile("<title>(.*)</title>")
                        .matcher(response.toString())
                        .group();

//                // Find all div elements and capture the id attribute and the contents of the
//                // element
//                String divPattern = "<div[^>]*id=\"([^\"]*)\">(.*)</div>";
//                Matcher matcher = Pattern.compile(divPattern)
//                        .matcher(response.toString());
//                List<String[]> divElements = (List<String[]>) matcher;
//
//
//                for (int i = 1; i <= matcher.groupCount(); i++) {
//                    String group = matcher.group(i);
//                    // 对匹配的组进行操作
//
//                }
//
//                        .results()
//                        .map(m -> new String[]{m.group(1), m.group(2)})
//                        .collect(Collectors.toList());
//
//                // Loop through the div elements and find the one with the "message" id
//                String messageText = "";
//                for (String[] div : divElements) {
//                    String divId = div[0];
//                    String divContent = div[1];
//                    if (divId.equals("message")) {
//                        messageText = divContent;
//                        break;
//                    }
//                }
                // Concatenate the title and message text
                errorDesc = titleText;
            } catch (Exception ex) {
                ex.printStackTrace();
//                errorDesc = (String) ((Map) JSON.parse(response.toString())).get("detail");
//                if (errorDesc.containsKey("message")) {
//                    errorDesc = (String) errorDesc.get("message");
//                }
            } finally {
                System.out.println(response.toString());
                throw new RuntimeException("Response is not in the correct format " + errorDesc);
            }
        }

    }

    public Map<String, Object> getChatResponse(String prompt, String output) {
        Map<String, Object> data = new HashMap<>();
        data.put("action", "next");
        data.put("conversation_id", this.conversationId);
        data.put("parent_message_id", this.parentId);
        data.put("model", "text-davinci-002-render");

        Map<String, Object> message = new HashMap<>();
        message.put("id", UUID.randomUUID().toString());
        message.put("role", "user");
        Map<String, Object> content = new HashMap<>();
        content.put("content_type", "text");
        content.put("parts", Collections.singletonList(prompt));
        message.put("content", content);
        data.put("messages", Collections.singletonList(message));

        this.conversationIdPrev = this.conversationId;
        this.parentIdPrev = this.parentId;

        if (output.equals("text")) {
            return this.getChatText(data);
        } else if (output.equals("stream")) {
            return this.getChatStream(data);
        } else {
            throw new RuntimeException("Output must be either 'text' or 'stream'");
        }
    }


    @SneakyThrows
    public void refreshSession() {
        if (!config.containsKey("session_token") && (!config.containsKey("email") ||
                !config.containsKey("password"))) {
            throw new RuntimeException("No tokens provided");
        } else if (config.containsKey("session_token")) {
            String sessionToken = config.get("session_token");
            if (sessionToken == null || sessionToken.equals("")) {
                throw new RuntimeException("No tokens provided");
            }
            Session session = new Session();

            // Set proxies
            if (config.get("proxy") != null && !config.get("proxy").equals("")) {
                Map<String, String> proxies = new HashMap<>();
                proxies.put("http", config.get("proxy"));
                proxies.put("https", config.get("proxy"));
                session.setProxies(proxies);
            }

            // Set cookies
            session.getCookies().put("__Secure-next-auth.session-token", config.get(
                    "session_token"));

            String urlSession = "https://chat.openai.com/api/auth/session";
            HttpResponse response = session.get2(urlSession,
                    Collections.singletonMap(
                            "User-Agent",
                            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15" +
                                    " (KHTML," +
                                    " like Gecko) Version/16.1 Safari/605.1.15 "
                    ));

            try {
                String name = "__Secure-next-auth.session-token";
                String cookieValue = response.getCookieValue(name);
                config.put("session_token", cookieValue);

                String body = response.body();
                System.out.println("session_token: " + cookieValue);
                JSONObject responseObject = JSON.parseObject(body);

                String accessToken = responseObject.getString("accessToken");
                System.out.println("accessToken: " + accessToken);

                config.put("Authorization", accessToken);


//                List<Cookie> cookies = Cookie.parseAll(HttpUrl.parse(urlSession),
//                        response.headers());
//                for (Cookie cookie1 : cookies) {
//                    if (cookie1.name().equals(name)) {
//                        config.put("session_token", response.getCookieValue(name));
//                    }
//                }

                this.refreshHeaders();
            } catch (Exception e) {
                System.out.println("Error refreshing session");
//                System.out.println(response.toString());
                throw new Exception("Error refreshing session", e);
            }
        } else if (config.containsKey("email") && config.containsKey("password")) {
            try {
                this.login(config.get("email"), config.get("password"));
            } catch (Exception e) {
                System.out.println("Error refreshing session: ");
                System.out.println(e);
                throw e;
            }
        } else {
            throw new RuntimeException("No tokens provided");
        }
    }


    public void login(String email, String password) {
        System.out.println("Logging in...");
        boolean useProxy = false;
        String proxy = null;
        if (config.containsKey("proxy")) {
            if (!config.get("proxy").equals("")) {
                useProxy = true;
                proxy = config.get("proxy");
            }
        }
        OpenAIAuth auth = new OpenAIAuth(email, password, useProxy, proxy);
        try {
            auth.begin();
        } catch (Exception e) {
            // if RuntimeException with e as "Captcha detected" fail
            if (e.getMessage().equals("Captcha detected")) {
                System.out.println("Captcha not supported. Use session tokens instead.");
                throw new RuntimeException("Captcha detected", e);
            }
            throw new RuntimeException("Error logging in", e);
        }
        if (auth.getAccessToken() != null) {
            config.put("Authorization", auth.getAccessToken());
            if (auth.getSessionToken() != null) {
                config.put("session_token", auth.getSessionToken());
            } else {
                String possibleTokens = auth.getSession().getCookies().get("__Secure-next-auth" +
                        ".session-token");
                if (possibleTokens != null) {
                    if (possibleTokens.length() > 1) {
                        config.put("session_token", possibleTokens);
//                        config.put("session_token", possibleTokens[0]);
                    } else {
                        try {
                            config.put("session_token", possibleTokens);
                        } catch (Exception e) {
                            throw new RuntimeException("Error logging in", e);
                        }
                    }
                }
            }
            this.refreshHeaders();
        } else {
            throw new RuntimeException("Error logging in");
        }
    }

    public void rollbackConversation() {
        this.conversationId = this.conversationIdPrev;
        this.parentId = this.parentIdPrev;
    }

    @SneakyThrows
    public static JSONObject resJson(Response response) {
        JSONObject responseObject = null;
        String text = response.body().string();
        try {
            response.body().close();
            responseObject = JSON.parseObject(text);
        } catch (Exception e) {
            System.out.println("json err, body: " + text);
            throw new RuntimeException(e);
        }

        return responseObject;
    }

}