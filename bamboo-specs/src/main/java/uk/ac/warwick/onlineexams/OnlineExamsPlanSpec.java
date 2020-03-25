package uk.ac.warwick.onlineexams;

import com.atlassian.bamboo.specs.api.BambooSpec;
import com.atlassian.bamboo.specs.api.builders.deployment.Deployment;
import com.atlassian.bamboo.specs.api.builders.notification.Notification;
import com.atlassian.bamboo.specs.api.builders.plan.Job;
import com.atlassian.bamboo.specs.api.builders.plan.Plan;
import com.atlassian.bamboo.specs.api.builders.plan.Stage;
import com.atlassian.bamboo.specs.api.builders.plan.artifact.Artifact;
import com.atlassian.bamboo.specs.api.builders.project.Project;
import com.atlassian.bamboo.specs.api.builders.requirement.Requirement;
import com.atlassian.bamboo.specs.builders.notification.DeploymentStartedAndFinishedNotification;
import com.atlassian.bamboo.specs.builders.task.*;
import com.atlassian.bamboo.specs.model.task.ScriptTaskProperties;
import uk.ac.warwick.bamboo.specs.AbstractWarwickBuildSpec;

import java.util.Collection;
import java.util.Collections;

/**
 * Plan configuration for Bamboo.
 * Learn more on: <a href="https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs">https://confluence.atlassian.com/display/BAMBOO/Bamboo+Specs</a>
 */
@BambooSpec
public class OnlineExamsPlanSpec extends AbstractWarwickBuildSpec {

  private static final Project PROJECT =
    new Project()
      .key("EXAMS")
      .name("Exam Management");

  private static final String LINKED_REPOSITORY = "Online Exams";

  private static final String SLACK_CHANNEL = "#onlineexams";

  private VcsCheckoutTask checkoutTask = new VcsCheckoutTask()
    .description("Checkout source from default repository")
    .checkoutItems(new CheckoutItem().defaultRepository());

  private NpmTask npmCiTask = new NpmTask()
    .description("dependencies")
    .nodeExecutable("Node 13")
    .command("ci");

  private Requirement linuxRequirement =
    new Requirement("Linux").matchValue("true").matchType(Requirement.MatchType.EQUALS);

  public static void main(String[] args) {
    new OnlineExamsPlanSpec().publish();
  }

  @Override
  protected Collection<Plan> builds() {
    return Collections.singleton(
      build(PROJECT, "ONLINE", "Online Exams")
        .linkedRepository(LINKED_REPOSITORY)
        .description("Build application")
        .stage(
          new Stage("Build stage")
            .jobs(
              // do all the test types in parallel
              testAndPackageJob(),
              mochaJob(),
              integrationTestJob()
            )
        )
        .slackNotifications(SLACK_CHANNEL, false)
        .build()
    );
  }

  private Job testAndPackageJob() {
    return new Job("Build and check", "BUILD")
      .tasks(
        checkoutTask,
        npmCiTask,
        new ScriptTask()
          .description("Run tests and package")
          .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
          .location(ScriptTaskProperties.Location.FILE)
          .fileFromPath("sbt")
          .argument("clean test universal:packageZipTarball")
          .environmentVariables("PATH=/usr/nodejs/13/bin")
      )
      .finalTasks(
        TestParserTask.createJUnitParserTask()
          .description("Parse test results")
          .resultDirectories("**/test-reports/*.xml")
      )
      .artifacts(
        new Artifact()
          .name("tar.gz")
          .copyPattern("app.tar.gz")
          .location("target/universal")
          .shared(true)
      )
      .requirements(linuxRequirement);
  }

  private Job mochaJob() {
    return new Job("Mocha", "JS")
      .tasks(
        checkoutTask,
        npmCiTask,
        new NpmTask()
          .description("JS Tests")
          .nodeExecutable("Node 13")
          .command("run bamboo")
      )
      .finalTasks(
        TestParserTask.createMochaParserTask()
          .defaultResultDirectory()
      )
      .requirements(linuxRequirement);
  }

  private Job integrationTestJob() {
    return new Job("Integration tests", "INT")
      .tasks(
        checkoutTask,
        npmCiTask,
        new ScriptTask()
          .description("Run tests and package")
          .interpreter(ScriptTaskProperties.Interpreter.BINSH_OR_CMDEXE)
          .location(ScriptTaskProperties.Location.FILE)
          .fileFromPath("sbt")
          .argument("clean integration/clean integration/test")
          .environmentVariables("PATH=/usr/nodejs/13/bin")
      )
      .finalTasks(
        TestParserTask.createJUnitParserTask()
          .description("Parse test results")
          .resultDirectories("**/test-reports/*.xml")
      )
      .artifacts(
        new Artifact()
          .name("Integration")
          .copyPattern("**")
          .location("it/target/test-html")
          .shared(true),
        new Artifact()
          .name("Integration screenshots")
          .copyPattern("**")
          .location("it/target/screenshots")
          .shared(true)
      )
      .requirements(linuxRequirement);
  }

  @Override
  protected Collection<Deployment> deployments() {
    return Collections.singleton(
      deployment(PROJECT, "ONLINE", "Online Exams")
        .autoPlayEnvironment("Development", "onlineexams-dev.warwick.ac.uk", "onlineexams", "dev", SLACK_CHANNEL)
        .autoPlayEnvironment("Test", "onlineexams-test.warwick.ac.uk", "onlineexams", "test", SLACK_CHANNEL)
        .autoPlayEnvironment("Sandbox", "onlineexams-sandbox.warwick.ac.uk", "onlineexams", "sandbox", SLACK_CHANNEL, "master")
        .playEnvironment("Production", "onlineexams.warwick.ac.uk", "onlineexams", "prod",
          env -> env.notifications(
            new Notification()
              .type(new DeploymentStartedAndFinishedNotification())
              .recipients(slackRecipient(SLACK_CHANNEL))
          )
        )
        .build()
    );
  }

}
