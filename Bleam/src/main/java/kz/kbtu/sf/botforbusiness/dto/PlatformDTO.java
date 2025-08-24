package kz.kbtu.sf.botforbusiness.dto;

import kz.kbtu.sf.botforbusiness.model.PlatformStatus;
import kz.kbtu.sf.botforbusiness.model.PlatformType;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PlatformDTO {

    private PlatformType platformType;
    private PlatformStatus platformStatus;
}
