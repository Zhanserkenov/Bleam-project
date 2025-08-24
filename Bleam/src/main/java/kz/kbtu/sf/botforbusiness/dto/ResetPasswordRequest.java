package kz.kbtu.sf.botforbusiness.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResetPasswordRequest {

    private String token;
    private String newPassword;
}
