/* Nick Miller
*  November 23, 2020
*  Turing Machine
*/

import javax.swing.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TM implements Runnable {
  public static final int MIN_SPEED = 1;
  public static final int DEFAULT_SPEED = 50;
  public static final int MAX_SPEED = 100;

  private static final int MIN_MS_TO_SLEEP = 100;
  private static final int MAX_MS_TO_SLEEP = 2000;

  private int speed;
  private boolean pause;
  private JTextArea outputTextArea;

  // Definition variables.
  private boolean hasDefinition;
  private String[] states;
  private String[] inputAlphabet;
  private String[] tapeAlphabet;
  private String startState;
  private String acceptState;
  private String rejectState;
  private String[][] transitionFuncs;
  // Input variables.
  private boolean hasInputs;
  private String[] originalTapeStrings;
  private String[] currentTapeStrings;
  private int tapeIndex = 0;
  private int tapePosition = 0;
  private String currentState;

  public static class InvalidTMDefinition extends Exception {
    public InvalidTMDefinition(String message) {
      super(message);
    }
  }

  public static class InvalidTMInputs extends Exception {
    public InvalidTMInputs(String message) {
      super(message);
    }
  }

  public TM(JTextArea outputTextArea) {
    this.outputTextArea = outputTextArea;

    speed = DEFAULT_SPEED;
    pause = true;

    Thread thread = new Thread(this);
    thread.start();

    hasDefinition = false;
    hasInputs = false;
  }

  @Override
  public void run() {
    while (true) {
      if (!pause) {
        singleStep();
      }

      try {
        Thread.sleep(
            (long)
                (MAX_MS_TO_SLEEP
                    - ((MAX_MS_TO_SLEEP - MIN_MS_TO_SLEEP)
                        / (double) (MAX_SPEED - MIN_SPEED)
                        * speed)));
      } catch (Exception ignored) {
      }
    }
  }

  public void singleStep() {
    if (!hasDefinition || !hasInputs || tapeIndex >= currentTapeStrings.length) {
      setPause(true);
      return;
    }

    String tapeString = currentTapeStrings[tapeIndex];
    String tapeChar;
    try {
      tapeChar = tapeString.substring(tapePosition, tapePosition + 1);
    } catch (IndexOutOfBoundsException ignored) {
      tapeChar = "_";
      tapeString += "_";
    }

    outputTextArea.append(String.format("   %s    %s\n\n", currentState, tapeChar));
    outputTextArea.append(String.format(" %s\n", tapeString));
    outputTextArea.append(String.format(" %s^\n\n\n", " ".repeat(tapePosition)));

    String[] transitionFunc = getTransitionFunc(currentState, tapeChar);
    if (transitionFunc == null
        || currentState.equals(rejectState)
        || currentState.equals(acceptState)) {
      currentTapeStrings[tapeIndex] = tapeString;
      outputTextArea.append("TM is halted now\n");
      outputTextArea.append(
          String.format("originalTapeString:  %s\n", originalTapeStrings[tapeIndex]));
      outputTextArea.append(
          String.format("current tapeString:  %s\n\n\n", currentTapeStrings[tapeIndex]));
      outputTextArea.append("                                                    ");
      if (currentState.equals(acceptState)) {
        outputTextArea.append("ACCEPTED\n");
      } else if (currentState.equals(rejectState)) {
        outputTextArea.append("REJECTED\n");
      } else {
        outputTextArea.append("REJECTED (NO STATE-INPUT PAIR)\n");
      }
      outputTextArea.append("\n\n\n");
      // Move on to the next input.
      tapeIndex += 1;
      tapePosition = 0;
      currentState = startState;
      return;
    }

    tapeString =
        tapeString.substring(0, tapePosition)
            + transitionFunc[3]
            + tapeString.substring(tapePosition + 1);

    currentState = transitionFunc[2];
    if (transitionFunc[4].equals("R")) {
      tapePosition += 1;
    } else {
      tapePosition -= 1;
    }
    currentTapeStrings[tapeIndex] = tapeString;
  }

  public void processDefinition(String sourceUrl) throws InvalidTMDefinition {
    setPause(true);
    String[] data = filterCommentsAndBlankLines(simpleReadData(sourceUrl));
    if (data.length <= 6) {
      throw new InvalidTMDefinition(
          String.format(
              "Expected at least 7 lines containing TM definition; found %d", data.length));
    }

    states = data[0].split("\\s+");
    if (states.length == 0) {
      throw new InvalidTMDefinition("Expected a TM to contain at least one state; found 0");
    }
    for (String state : states) {
      if (!state.matches("[a-z][a-zA-Z]*")) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected state data line to be lowercase alpha characters separated by single spaces; found '%s'",
                state));
      }
    }
    if (Arrays.stream(states).distinct().count() != states.length) {
      throw new InvalidTMDefinition("State set contains duplicate states");
    }

    inputAlphabet = data[1].split("\\s+");
    for (String input : inputAlphabet) {
      if (input.length() != 1
          || !(Character.isDigit(input.charAt(0)))
          || Character.isLetter(input.charAt(0))) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected input alphabet data line to contain single alpha-numeric characters separated by single spaces; found '%s'",
                input));
      }
    }
    if (Arrays.stream(inputAlphabet).distinct().count() != inputAlphabet.length) {
      throw new InvalidTMDefinition("Input alphabet contains duplicate characters");
    }
    if (inputAlphabet.length > states.length) {
      throw new InvalidTMDefinition(
          String.format(
              "Expected input alphabet to be less than or equal to the number of states; found %d states and %d alphabet characters",
              states.length, inputAlphabet.length));
    }

    tapeAlphabet = data[2].split("\\s+");
    for (String tape : tapeAlphabet) {
      if (tape.length() != 1
          || !(Character.isDigit(tape.charAt(0))
              || Character.isLetter(tape.charAt(0))
              || tape.equals("_"))) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected tape alphabet data line to contain single alpha-numeric characters or underscores separated by single spaces; found '%s'",
                tape));
      }
    }
    if (Arrays.stream(tapeAlphabet).noneMatch("_"::equals)) {
      throw new InvalidTMDefinition("Tape alphabet must contain '_'; found none");
    }
    if (Arrays.stream(tapeAlphabet).distinct().count() != tapeAlphabet.length) {
      throw new InvalidTMDefinition("Tape alphabet contains duplicate characters");
    }
    if (!Arrays.asList(tapeAlphabet).containsAll(Arrays.asList(inputAlphabet))) {
      throw new InvalidTMDefinition("Input alphabet contains characters not in the tape alphabet");
    }

    startState = data[3].trim();
    if (!Arrays.asList(states).contains(startState)) {
      throw new InvalidTMDefinition(
          String.format(
              "Expected start state to be one of the allowed states %s; found '%s'",
              Arrays.toString(states), startState));
    }

    acceptState = data[4].trim();
    if (!Arrays.asList(states).contains(acceptState)) {
      throw new InvalidTMDefinition(
          String.format(
              "Expected accept state to be one of the allowed states %s; found '%s'",
              Arrays.toString(states), acceptState));
    }

    rejectState = data[5].trim();
    if (!Arrays.asList(states).contains(rejectState)) {
      throw new InvalidTMDefinition(
          String.format(
              "Expected reject state to be one of the allowed states %s; found '%s'",
              Arrays.toString(states), rejectState));
    }
    if (acceptState.equals(rejectState)) {
      throw new InvalidTMDefinition("Accept state cannot equal reject state");
    }

    List<String[]> transitionFuncsList = new ArrayList<>();
    for (int i = 6; i < data.length; i++) {
      String[] transitionFunc = data[i].split("\\s+");
      if (transitionFunc.length < 5) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition to contain at least 5 entries; found %d",
                transitionFunc.length));
      }
      if (!Arrays.asList(states).contains(transitionFunc[0])) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition state to be one of the allowed states %s; found '%s'",
                Arrays.toString(states), transitionFunc[0]));
      }
      if (!Arrays.asList(tapeAlphabet).contains(transitionFunc[1])) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition input to be one of the allowed tape input characters %s; found '%s'",
                Arrays.toString(states), transitionFunc[1]));
      }
      if (!Arrays.asList(states).contains(transitionFunc[2])) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition state to be one of the allowed states %s; found '%s'",
                Arrays.toString(states), transitionFunc[2]));
      }
      if (!Arrays.asList(tapeAlphabet).contains(transitionFunc[3])) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition input to be one of the allowed tape input characters %s; found '%s'",
                Arrays.toString(states), transitionFunc[3]));
      }
      if (!(transitionFunc[4].contains("L") || transitionFunc[4].contains("R"))) {
        throw new InvalidTMDefinition(
            String.format(
                "Expected transition input to be one of the allowed tape input characters L or R; found '%s'",
                transitionFunc[4]));
      }
      transitionFuncsList.add(Arrays.copyOf(transitionFunc, 5));
    }
    transitionFuncs = transitionFuncsList.toArray(String[][]::new);
    currentState = startState;
    hasDefinition = true;
  }

  public void processInputs(String inputUrl) throws InvalidTMInputs {
    setPause(true);
    String[] inputs = filterCommentsAndBlankLines(simpleReadData(inputUrl));
    for (String input : inputs) {
      for (char inputChar : input.toCharArray()) {
        if (!Arrays.asList(inputAlphabet).contains(String.valueOf(inputChar))) {
          throw new InvalidTMInputs(
              String.format(
                  "Expected input to be one of the allowed input characters %s; found '%s'",
                  Arrays.toString(inputAlphabet), inputChar));
        }
      }
    }
    originalTapeStrings = inputs;
    currentTapeStrings = originalTapeStrings.clone();
    tapeIndex = 0;
    tapePosition = 0;
    currentState = startState;
    hasInputs = true;
  }

  private String[] getTransitionFunc(String state, String input) {
    for (String[] transitionFunc : transitionFuncs) {
      if (transitionFunc[0].equals(state) && transitionFunc[1].equals(input)) {
        return transitionFunc;
      }
    }
    return null;
  }

  public void setPause(boolean pause) {
    this.pause = pause;
  }

  public boolean isPaused() {
    return pause;
  }

  public void setSpeed(int speed) {
    this.speed = Math.min(MAX_SPEED, Math.max(MIN_SPEED, speed));
  }

  public static String[] filterCommentsAndBlankLines(String[] info) {
    if (info == null) {
      return null;
    }
    return Arrays.stream(info)
        .filter(
            // Ignore comments and blank lines
            (line) -> !line.matches("^\\s*(#.*?)?$"))
        .toArray(String[]::new);
  }

  public static String[] simpleReadData(String urlName) {
    String[] info = null;

    try {
      URL url = new URL(urlName);
      In fileInput = new In(url);

      if (fileInput.exists()) {
        System.out.println("fileInput.exists()   ");
        String inputData = fileInput.readAll();
        info = inputData.split("\n");

      } else {
        System.out.println("   NOT   fileInput.exists()   ");
        System.out.println("could not find url:  " + urlName + "\n\n");
      }
    } catch (MalformedURLException ex) {
      System.out.println("error trying to read URL:\n  " + urlName);
    }
    return info;
  }
}
