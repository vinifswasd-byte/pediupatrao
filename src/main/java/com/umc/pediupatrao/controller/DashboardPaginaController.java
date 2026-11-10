package com.umc.pediupatrao.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Tela do dashboard. A página em si não traz dados: o JavaScript dela
 * busca tudo na API /api/dashboard, como pede o RF06.
 */
@Controller
public class DashboardPaginaController {

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("content", "dashboard :: content");
        return "layout";
    }
}
