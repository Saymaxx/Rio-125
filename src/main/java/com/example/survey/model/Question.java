// Question.java
package main.java.com.example.survey.model;

import jakarta.persistence.*;

@Entity
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long questionId;
    private String attribute;  // e.g., "Food Quality"
    private String questionText;
    // Getters and setters (generate these in your IDE)
}