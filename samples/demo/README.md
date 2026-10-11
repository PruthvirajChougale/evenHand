# Demo data

Six marks files for showing Even Hand, including two online exams exported as they come from an
LMS and from Google Forms. **All made up**: the students, roll numbers and TAs are
fictional, and no real marks are involved. Each file was built with known TA biases planted in
it, so the results can be checked against the right answer.

The course: 150 students from CE, ME, AE, EE and CH, two quizzes (4 × 5 marks) and an end-sem
(6 questions, 80 marks), weighted 20 / 20 / 60, marked by four TAs. Scripts were dealt to the TAs
at random, about 1% of students missed each exam, and marks are in half marks.

## The planted answer key

| TA | Planted severity | Meaning |
| --- | --- | --- |
| Ravi | **+6** | harsh: about 0.6 marks less on a 10-mark question |
| Arjun | +1 | about average |
| Divya | −2 | slightly lenient |
| Meena | **−5** | lenient: about 0.5 marks more on a 10-mark question |

Severity is in percentage points of a question's maximum, and the four sum to zero.

## The files, and what each one shows

| File | Story | What Even Hand says (checked) |
| --- | --- | --- |
| `1-course-marks.csv` | **The main demo.** The whole course, one row per answer, with 10% of scripts marked a second time for moderation (`role` = `second`). | Every TA on one scale. Ravi **+5.6**, Arjun +1.2, Divya −1.8, Meena **−5.0**, each within half a point of the truth. p = 0.01. 15 letter grades move; **15 scripts to re-check**. |
| `2-endsem-marksheet.csv` | The end-sem as the sheet professors already keep: one row per student, `Q1 (10)` … `Q6 (20)`, Total. Opens in Excel. The last 41 rows repeat students already listed: those scripts were marked a second time by another TA. | Ravi **+5.3**, Arjun +1.4, Divya −1.9, Meena **−4.8**. 20 scripts to re-check. It notes that a second row for a student is read as a second marking, with the first row counting. |
| `3-fair-marking.csv` | Same course, but all four TAs mark alike. | Every severity within ±0.3; p = 0.49: **"within what chance alone produces, so no script is flagged."** It does not invent a problem. |
| `5-online-quiz-export.csv` | An **online end-sem on an LMS** (Moodle-style grades export): e-mail addresses, `Grade/80.00`, `Q. 1 /10.00` … `Q. 6 /20.00`, a `Graded by` column, 25% of attempts graded a second time, and the "Overall average" row. | Read as exported. Ravi **+5.1**, Arjun +1.0, Divya −1.1, Meena **−5.0**. 20 scripts to re-check. |
| `6-google-form-responses.csv` | An **online quiz on Google Forms**: Timestamp, e-mail, `Score` written `23.5 / 40`, roll number, the written answers, and a `TA` column. 25% of responses graded a second time. | Read as exported, with the score as the mark out of 40. Ravi **+5.3**, Arjun +1.1, Divya −2.5, Meena **−3.9**: slightly less precise, because a form exports only each student's total. |
| `4-question-wise.csv` | End-sem only; each TA marks the same questions for everyone (Ravi Q1 and Q5, Meena Q2 and Q6, …). | **Safe by design**: every student met the same TAs in the same proportions, so a harsh TA cannot have moved anyone relative to anyone else. Nothing is estimated. |

## How to run them

Browser: double-click `even-hand.cmd`, drop a file in, set the weights (Quiz 1 = 20, Quiz 2 = 20,
End-sem = 60 for files 1 and 3), press *Check the marking*.

Terminal:

```
java -jar eh-app/target/even-hand.jar run samples/demo/1-course-marks.csv --weights "Quiz 1=20,Quiz 2=20,End-sem=60"
java -jar eh-app/target/even-hand.jar run samples/demo/2-endsem-marksheet.csv
java -jar eh-app/target/even-hand.jar run samples/demo/3-fair-marking.csv --weights "Quiz 1=20,Quiz 2=20,End-sem=60"
java -jar eh-app/target/even-hand.jar run samples/demo/4-question-wise.csv
java -jar eh-app/target/even-hand.jar run samples/demo/5-online-quiz-export.csv
java -jar eh-app/target/even-hand.jar run samples/demo/6-google-form-responses.csv
```

## A 60-second demo script

1. Open `2-endsem-marksheet.csv` in Excel: "This is an ordinary end-sem mark sheet, with one column
   saying which TA marked the script."
2. Drop `1-course-marks.csv` into the page and run it. Read the bottom line aloud: Ravi is the
   harshest, Meena the most lenient, how many grades move, and the next step.
3. Show the answer key above: "We planted these biases. It found each within half a point."
4. Run `3-fair-marking.csv`: "When the TAs agree, it says so. It does not invent work."
5. Run `4-question-wise.csv`: "When nobody can have been harmed, it explains why, and stops."
6. Drop `6-google-form-responses.csv` in as downloaded: "A Google Forms quiz, straight from the
   export, plus one column saying which TA graded each response. Same answer."
