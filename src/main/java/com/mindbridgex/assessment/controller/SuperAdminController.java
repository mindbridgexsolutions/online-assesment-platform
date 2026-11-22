package com.mindbridgex.assessment.controller;

import com.mindbridgex.assessment.exception.AppException;
import com.mindbridgex.assessment.repository.ExamRepository;
import com.mindbridgex.assessment.repository.QuestionRepository;
import com.mindbridgex.assessment.security.AuthUser;
import com.mindbridgex.assessment.service.BranchService;
import com.mindbridgex.assessment.service.CoPoService;
import com.mindbridgex.assessment.service.LiveCorrectionService;
import com.mindbridgex.assessment.service.OverrideService;
import com.mindbridgex.assessment.util.Flash;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.List;

/** The Super Administrator's console: branches, overrides, live exam correction and programme outcomes. */
@Controller
@RequestMapping("/super")
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SuperAdminController {
    private final BranchService branches;
    private final OverrideService overrides;
    private final LiveCorrectionService live;
    private final CoPoService copo;
    private final ExamRepository exams;
    private final QuestionRepository questions;

    public SuperAdminController(BranchService branches, OverrideService overrides, LiveCorrectionService live, CoPoService copo, ExamRepository exams, QuestionRepository questions) {
        this.branches = branches;
        this.overrides = overrides;
        this.live = live;
        this.copo = copo;
        this.exams = exams;
        this.questions = questions;
    }

    // ------------------------------------------------------------ dashboard
    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("totals", branches.totals());
        model.addAttribute("stats", branches.stats());
        return "super/dashboard";
    }

    // ------------------------------------------------------------ branches
    @GetMapping("/branches")
    public String branchList(Model model) {
        model.addAttribute("stats", branches.stats());
        return "super/branches";
    }

    @PostMapping("/branches")
    public String branchCreate(@RequestParam String name, @RequestParam String code, HttpServletResponse res) {
        try {
            branches.create(name, code);
            Flash.success(res, "Branch added.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/branches";
    }

    @PostMapping("/branches/{id}/active")
    public String branchActive(@PathVariable Long id, @RequestParam boolean active, HttpServletResponse res) {
        branches.setActive(id, active);
        Flash.success(res, active ? "Branch activated." : "Branch deactivated.");
        return "redirect:/super/branches";
    }

    @GetMapping("/branches/{id}")
    public String branchDetail(@PathVariable Long id, Model model) {
        model.addAttribute("branch", branches.get(id));
        for (String k : List.of("subject", "batch", "teacher")) {
            model.addAttribute(k + "In", branches.members(k, id));
            model.addAttribute(k + "Out", branches.candidates(k, id));
        }
        return "super/branch-detail";
    }

    @PostMapping("/branches/{id}/assign")
    public String branchAssign(@PathVariable Long id, @RequestParam String kind, @RequestParam Long itemId, @RequestParam boolean add, HttpServletResponse res) {
        try {
            branches.assign(kind, itemId, add ? id : null);
            Flash.success(res, add ? "Added to the branch." : "Removed from the branch.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/branches/" + id;
    }

    // ------------------------------------------------------------ approvals
    @GetMapping("/overrides")
    public String overridesPage(Model model) {
        model.addAttribute("exams", exams.findAll(PageRequest.of(0, 50, Sort.by("id").descending())).getContent());
        model.addAttribute("questions", questions.findAll(PageRequest.of(0, 50, Sort.by("id").descending())).getContent());
        return "super/overrides";
    }

    @PostMapping("/overrides/exam/{id}")
    public String examOverride(@PathVariable Long id, @RequestParam boolean approve, @RequestParam(required = false) String reason, @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        try {
            overrides.examApproval(me, id, approve, reason);
            Flash.success(res, approve ? "Exam approved." : "Exam rejected and withdrawn from students.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/overrides";
    }

    @PostMapping("/overrides/question/{id}")
    public String questionOverride(@PathVariable Long id, @RequestParam boolean approve, @RequestParam(required = false) String reason, @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        try {
            overrides.questionApproval(me, id, approve, reason);
            Flash.success(res, approve ? "Question approved." : "Question rejected. It will not be used in new exams.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/overrides";
    }

    // ------------------------------------------------------------ results hold
    @GetMapping("/results")
    public String results(Model model) {
        model.addAttribute("exams", live.examsToCorrect());
        model.addAttribute("now", java.time.Instant.now());
        return "super/results";
    }

    @PostMapping("/results/{id}")
    public String releaseResults(@PathVariable Long id, @RequestParam(required = false) String at, @RequestParam(defaultValue = "false") boolean now, @RequestParam String reason,
                                 @AuthenticationPrincipal AuthUser me, HttpServletResponse res, com.mindbridgex.assessment.config.AppProperties props) {
        try {
            java.time.Instant when = now ? java.time.Instant.now() : (at == null || at.isBlank() ? null : java.time.LocalDateTime.parse(at).atZone(props.zone()).toInstant());
            overrides.setResultRelease(me, id, when, reason);
            Flash.success(res, now ? "Results released to students." : "Result release date saved.");
        } catch (AppException | java.time.format.DateTimeParseException e) {
            Flash.error(res, e instanceof AppException ? e.getMessage() : "Enter a valid date and time.");
        }
        return "redirect:/super/results";
    }

    // ------------------------------------------------------------ live correction
    @GetMapping("/live")
    public String liveList(Model model) {
        model.addAttribute("exams", live.examsToCorrect());
        return "super/live";
    }

    @GetMapping("/live/{examId}")
    public String liveExam(@PathVariable Long examId, @AuthenticationPrincipal AuthUser me, Model model) {
        model.addAttribute("view", live.view(me, examId));
        return "super/live-exam";
    }

    @PostMapping("/live/{examId}/q/{qid}/accept")
    public String accept(@PathVariable Long examId, @PathVariable Long qid, @RequestParam(required = false) List<Long> optionIds, @RequestParam String reason,
                         @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        return run(res, examId, () -> "Extra options accepted. " + live.acceptOptions(me, examId, qid, optionIds, reason) + " submitted attempt(s) re-scored.");
    }

    @PostMapping("/live/{examId}/q/{qid}/push")
    public String push(@PathVariable Long examId, @PathVariable Long qid, @RequestParam String reason, @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        return run(res, examId, () -> "Corrected question sent to every attempt. " + live.pushEdit(me, examId, qid, reason) + " submitted attempt(s) re-scored.");
    }

    @PostMapping("/live/{examId}/q/{qid}/void")
    public String voidQuestion(@PathVariable Long examId, @PathVariable Long qid, @RequestParam String mode, @RequestParam String reason, @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        return run(res, examId, () -> "Done. " + live.setVoid(me, examId, qid, mode, reason) + " submitted attempt(s) re-scored.");
    }

    @PostMapping("/live/{examId}/recalculate")
    public String recalc(@PathVariable Long examId, @RequestParam String reason, @AuthenticationPrincipal AuthUser me, HttpServletResponse res) {
        return run(res, examId, () -> live.recalculate(me, examId, reason) + " submitted attempt(s) re-scored.");
    }

    private String run(HttpServletResponse res, Long examId, java.util.function.Supplier<String> action) {
        try {
            Flash.success(res, action.get());
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/live/" + examId;
    }

    // ------------------------------------------------------------ programme outcomes
    @GetMapping("/outcomes")
    public String outcomes(Model model) {
        model.addAttribute("pos", copo.allPos());
        model.addAttribute("branches", branches.active());
        return "super/outcomes";
    }

    @PostMapping("/outcomes")
    public String addPo(@RequestParam(required = false) Long branchId, @RequestParam String code, @RequestParam(required = false) String description, HttpServletResponse res) {
        try {
            copo.addPo(branchId, code, description);
            Flash.success(res, "Programme Outcome added.");
        } catch (AppException e) {
            Flash.error(res, e.getMessage());
        }
        return "redirect:/super/outcomes";
    }

    @PostMapping("/outcomes/{id}/delete")
    public String deletePo(@PathVariable Long id, HttpServletResponse res) {
        copo.deletePo(id);
        Flash.success(res, "Programme Outcome removed.");
        return "redirect:/super/outcomes";
    }
}
