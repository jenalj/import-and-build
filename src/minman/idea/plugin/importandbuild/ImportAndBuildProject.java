package minman.idea.plugin.importandbuild;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationAction;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.task.ProjectTaskManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.maven.project.MavenImportListener;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class ImportAndBuildProject implements ProjectComponent {
    private static final String MESSAGE_GROUP_ID = "ImportAndBuild";
    private static final String FILE_TOKEN = "import-and-build.token";

    @NotNull
    @Override
    public String getComponentName() {
        return "ImportAndBuildProject";
    }

    public ImportAndBuildProject(Project project) {
        File tokenFile = new File(project.getBasePath(), FILE_TOKEN);
        if (tokenFile.exists() && tokenFile.isFile()) {
            if (!tokenFile.delete()) {
                Notifications.Bus.notify(new Notification(MESSAGE_GROUP_ID, "ImportAndBuild", "Delete failed " + tokenFile, NotificationType.WARNING));
            }
            prepareExecution(project);
        }
    }

    private void prepareExecution(Project project) {
        AtomicBoolean wantExecution = new AtomicBoolean(true);
        // notify and let user abort
        Notification infoNotification = new Notification(MESSAGE_GROUP_ID, "ImportAndBuild", "Reimport all maven projects and Rebuild", NotificationType.INFORMATION);
        infoNotification.addAction(new NotificationAction("No, thanks!") {
            @Override
            public void actionPerformed(@NotNull AnActionEvent anActionEvent, @NotNull Notification notification) {
                wantExecution.set(false);
                notification.expire();
            }
        });
        Notifications.Bus.notify(infoNotification);

        DumbService.getInstance(project).runWhenSmart(() -> {
            if (wantExecution.get() && !project.isDisposed()) {
                // subscribe for maven import listener
                project.getMessageBus().connect().subscribe(MavenImportListener.TOPIC, (collection, list) -> {
                    if (wantExecution.getAndSet(false) && !project.isDisposed()) {
                        Notifications.Bus.notify(new Notification(MESSAGE_GROUP_ID, "ImportAndBuild", "Reimport All Maven Projects done", NotificationType.INFORMATION));
                        infoNotification.expire();
                        // rebuild project
                        ApplicationManager.getApplication().invokeLater(() -> {
                            if (!project.isDisposed()) {
                                ProjectTaskManager.getInstance(project).rebuildAllModules(null);
                            }
                        });
                    }
                });
                // run maven import
                MavenProjectsManager mavenProjectsManager = MavenProjectsManager.getInstance(project);
                mavenProjectsManager.forceUpdateAllProjectsOrFindAllAvailablePomFiles();
            }
        });
    }
}
