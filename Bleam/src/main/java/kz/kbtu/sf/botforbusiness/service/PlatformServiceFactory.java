package kz.kbtu.sf.botforbusiness.service;

import kz.kbtu.sf.botforbusiness.model.PlatformType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class PlatformServiceFactory {

    private final Map<PlatformType, PlatformService> serviceMap = new EnumMap<>(PlatformType.class);

    public PlatformServiceFactory(TelegramService telegramService, WhatsAppService whatsAppService) {
        serviceMap.put(PlatformType.TELEGRAM, telegramService);
        serviceMap.put(PlatformType.WHATSAPP, whatsAppService);
    }

    public PlatformService getService(PlatformType platformType) {
        PlatformService service = serviceMap.get(platformType);
        if (service == null) {
            throw new IllegalArgumentException("Unsupported platform: " + platformType);
        }
        return service;
    }
}
