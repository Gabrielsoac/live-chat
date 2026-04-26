package tech.gabrielsoac.livechatms.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.web.util.HtmlUtils;

import tech.gabrielsoac.livechatms.domain.ChatInput;
import tech.gabrielsoac.livechatms.domain.ChatOutput;

@Controller
public class LiveChatController {

    @MessageMapping("/new-message")
    @SendTo("/topics/livechat")
    public ChatOutput newMessage(ChatInput input) {
        return new ChatOutput(HtmlUtils.htmlEscape(input.username() + ": " + input.message()));
    }
}
