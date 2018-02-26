import java.io.IOException;
import java.util.TimerTask;

class SendStateHandler extends TimerTask {
	private Router router;
	public SendStateHandler(Router router){
		this.router = router;

	}

	public void run() {  
		try {
			this.router.processUpdateNeighbor();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}