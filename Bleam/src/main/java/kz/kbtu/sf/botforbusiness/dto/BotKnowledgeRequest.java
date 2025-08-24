package kz.kbtu.sf.botforbusiness.dto;

import kz.kbtu.sf.botforbusiness.model.SourceType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class BotKnowledgeRequest {

    private Long userId;
    private SourceType sourceType;
    private String content;
}
