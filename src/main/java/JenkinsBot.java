import com.offbytwo.jenkins.JenkinsServer;
import com.offbytwo.jenkins.model.Job;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JenkinsBot extends TelegramLongPollingBot {

    public static int phase;
    public static int itemNumber = 0;

    public static void main(String[] args) {
        ApiContextInitializer.init();
        TelegramBotsApi telegramBotsApi = new TelegramBotsApi();
        try {
            telegramBotsApi.registerBot(new JenkinsBot());
        } catch (TelegramApiRequestException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        JenkinsServer jenkins = null;
        try {
            jenkins = new JenkinsServer(
                    new URI("http://" + SecureData.jenkinsURL + ":8080/"), SecureData.jenkinsUsername, SecureData.jenkinsPassword);
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }
        List<Map.Entry<String, Job>> list = null;
        try {
            assert jenkins != null;
            list = new ArrayList<>(jenkins.getJobs().entrySet());
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (update.getMessage().getText().equals("/start")) {
            sendMsg(update, "Привет, " + update.getMessage().getFrom().getFirstName() + ". Выбери джобу:");
            for (int i = 1; i <= Objects.requireNonNull(list).size(); i++) {
                sendMsg(update, i + ") " + list.get(i - 1).getKey());
            }
            phase = 1;
        } else if (phase == 1) {
            String text = update.getMessage().getText();
            int number = 0;
            try {
                number = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                sendMsg(update, "Не понял. Введи цифру.");
                return;
            }
            if (number < 1 || number > Objects.requireNonNull(list).size()) {
                sendMsg(update, "Введи правильную цифру.");
            } else {
                sendMsg(update, "Выбрана джоба №" + number + " - " + list.get(number - 1).getKey() + ".");
                sendMsg(update, "Выбери действие:\n1) Запустить (только джобы без параметров)\n2) Показать информацию о последнем запуске");
                phase = 2;
                itemNumber = number;
            }
        } else if (phase == 2) {
            assert list != null;
            String jobName = list.get(itemNumber - 1).getKey();
            String text = update.getMessage().getText();
            int number = 0;
            try {
                number = Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                sendMsg(update, "Не понял. Введи цифру.");
                return;
            }
            if (number == 1) {
                phase = 4;
                sendMsg(update, "Запускаю джобу " + jobName + ". Сообщу, когда завершится.");
                try {
                    int jobNumber = jenkins.getJob(jobName).getLastCompletedBuild().getNumber();
                    System.out.println(jenkins.getJob(jobName).getName());
                    String cmd = String.format(
                            "curl -X POST http://%s:%s@%s:8080//job/%sbuild",
                            SecureData.jenkinsUsername, SecureData.jenkinsToken, SecureData.jenkinsURL, jenkins.getJob(jobName).getUrl()
                                    .replaceAll("http://.+/job/", ""));
                    System.out.println(cmd);
                    Runtime.getRuntime().exec(cmd);
                    while (jobNumber == jenkins.getJob(jobName).getLastCompletedBuild().getNumber()) {
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    phase = 0;
                    sendMsg(update, "Джоба " + jobName + " завершена.\n" + jenkins.getJob(jobName).getLastCompletedBuild().getUrl());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else if (number == 2) {
                try {
                    sendMsg(update, "Показываю инфу о последней завершённой джобе:\n" +
                            "id " + jenkins.getJob(jobName).getLastCompletedBuild().details().getId() + "\n" +
                            "Длительность " + jenkins.getJob(jobName).getLastCompletedBuild().details().getDuration() + "\n" +
                            "Результат " + jenkins.getJob(jobName).getLastCompletedBuild().details().getResult() + "\n" +
                            jenkins.getJob(jobName).getLastCompletedBuild().details().getUrl());
                    System.out.println(jenkins.getJob(jobName).getLastCompletedBuild());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                phase = 0;
            } else {
                sendMsg(update, "Введи правильную цифру.");
            }
        } else if (phase == 0) {
            sendMsg(update, "Выбери джобу:");
            for (int i = 1; i <= Objects.requireNonNull(list).size(); i++) {
                sendMsg(update, i + ") " + list.get(i - 1).getKey());
            }
            phase = 1;
        } else if (phase == 4) {
            sendMsg(update, "Подожди, джоба выполняется.");
        }
    }

    public void sendMsg(Update update, String text) {
        try {
            execute(new SendMessage().setChatId(update.getMessage().getChatId()).setText(text));
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getBotUsername() {
        return SecureData.telegramUsername;
    }

    @Override
    public String getBotToken() {
        return SecureData.telegramToken;
    }
}
