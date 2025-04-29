// Response.java
package main.java.com.example.survey.model;

import jakarta.persistence.*;

@Entity
public class Response {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long responseId;
    private String rating;  // "Highly Satisfied", etc.

    @ManyToOne
    @JoinColumn(name = "question_id")
    private Question question;
    // Getters and setters
}