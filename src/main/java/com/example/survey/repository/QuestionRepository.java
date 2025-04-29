// QuestionRepository.java
package main.java.com.example.survey.repository;

import com.example.survey.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QuestionRepository extends JpaRepository<Question, Long> {
    List<Question> findByAttribute(String attribute);
}