package rs.ac.uns.ftn.slagalica.domain.service;

public class StepByStepService {
    public int pointsForStep(int openedStepIndex) {
        int normalized = Math.max(0, Math.min(6, openedStepIndex));
        return 20 - (normalized * 2);
    }

    public boolean isCorrect(String expected, String actual) {
        return expected != null && actual != null && expected.trim().equalsIgnoreCase(actual.trim());
    }
}
