package kz.kbtu.sf.botforbusiness.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ForgotPasswordRequest {

    private String email;
}
