package kz.kbtu.sf.botforbusiness.dto;

import kz.kbtu.sf.botforbusiness.model.Role;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UserInfo {

    private Long id;
    private String email;
    private Role role;
}
