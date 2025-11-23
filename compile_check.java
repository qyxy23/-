import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import com.guanyu.haigui.service.ServicesImpl.TurtleSoupService;

public class compile_check {
    public static void main(String[] args) {
        HaiGuiSoup soup = new HaiGuiSoup();
        // 测试progressSettings方法是否存在
        // soup.setProgressSettings("{}");
        // String settings = soup.getProgressSettings();

        TurtleSoupService service = new TurtleSoupService();
        System.out.println("编译测试通过");
    }
}