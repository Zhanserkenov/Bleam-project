package kz.kbtu.sf.botforbusiness.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class QrPayload {

    private Long botId;
    private Long userId;
    private String qrCode;
    private Long timestamp;
}
