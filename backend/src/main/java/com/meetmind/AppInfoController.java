package com.meetmind;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class AppInfoController {
    @GetMapping("/info")
    public Map<String, String> info() { return Map.of("name", "RIMeet", "version", "0.5.4", "mode", "offline-first"); }
}
