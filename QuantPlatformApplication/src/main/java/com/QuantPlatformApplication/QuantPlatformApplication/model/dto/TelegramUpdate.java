// model/dto/TelegramUpdate.java
package com.QuantPlatformApplication.QuantPlatformApplication.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelegramUpdate {
    @JsonProperty("update_id")
    private Long updateId;

    private TelegramMessage message;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramMessage {
        @JsonProperty("message_id")
        private Long messageId;

        private TelegramChat chat;
        private String text;

        @JsonProperty("from")
        private TelegramUser fromUser;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramChat {
        private Long id;
        private String type;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TelegramUser {
        private Long id;
        @JsonProperty("first_name")
        private String firstName;
        private String username;
    }
}
