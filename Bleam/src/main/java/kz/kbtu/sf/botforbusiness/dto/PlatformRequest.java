package kz.kbtu.sf.botforbusiness.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlatformRequest {

    private Long userId;
    private String apiToken;
}
