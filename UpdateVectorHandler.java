import java.io.IOException;
import java.util.TimerTask;

class UpdateVectorHandler extends TimerTask {
	private Router router;
	public UpdateVectorHandler(Router router){
		this.router = router;

	}

	public void run() {  
		this.router.processUpdateRoute();
	}
}