package main.java.com.example.survey.controller;

import com.example.survey.model.Response;
import com.example.survey.repository.ResponseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SurveyController {
    @Autowired
    private ResponseRepository responseRepo;

    @GetMapping("/responses")
    public List<Response> getResponses(@RequestParam String attribute) {
        return responseRepo.findByQuestion_Attribute(attribute);
    }
}