package com.sandook.ledger.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * SPA fallback: forwards clean-URL routes to their per-route index.html
 * so the Next.js client-side router can handle them.
 * <p>
 * With {@code trailingSlash: true} + {@code output: "export"} Next.js generates
 * {@code login/index.html}, {@code dashboard/index.html}, etc. Spring Boot's
 * resource handler doesn't auto-resolve directory → index.html, so both
 * {@code /login} and {@code /login/} must be handled here.
 * <p>
 * The forward dispatch re-enters the Spring Security filter chain (even in
 * Spring Security 7), so every forwarded path <b>must</b> appear in
 * {@code PUBLIC_PATHS} in {@link com.sandook.ledger.security.SecurityConfig}.
 * <p>
 * In Docker mode the frontend runs as a separate container — this controller is
 * never reached because the API is the only thing the backend exposes.
 */
@Controller
public class SpaController {

    @GetMapping("/")
    public String root() {
        return "forward:/index.html";
    }

    @RequestMapping({"/login", "/login/"})
    public String login() {
        return "forward:/login/index.html";
    }

    @RequestMapping({"/dashboard", "/dashboard/"})
    public String dashboard() {
        return "forward:/dashboard/index.html";
    }

    @RequestMapping({"/cash", "/cash/"})
    public String cash() {
        return "forward:/cash/index.html";
    }

    @RequestMapping({"/petty-cash", "/petty-cash/"})
    public String pettyCash() {
        return "forward:/petty-cash/index.html";
    }

    @RequestMapping({"/parking", "/parking/"})
    public String parking() {
        return "forward:/parking/index.html";
    }

    @RequestMapping({"/transfers", "/transfers/"})
    public String transfers() {
        return "forward:/transfers/index.html";
    }

    @RequestMapping({"/audit", "/audit/"})
    public String audit() {
        return "forward:/audit/index.html";
    }

    @RequestMapping({"/users", "/users/"})
    public String users() {
        return "forward:/users/index.html";
    }
}
