public class CacheManager implements Runnable {
	private Thread thread;

	public void run() {
		while (true) {
			try {
				Thread.sleep(10000);
				while (Main.members.size() > Main.cacheSize) {
					System.out.println(String.format("Members: %s", Main.members.size()));
					Main.members.remove(0);
				}
				while (Main.channels.size() > Main.cacheSize) {
					System.out.println(String.format("Channels: %s", Main.channels.size()));
					Main.channels.remove(0);
				}
				while (Main.messages.size() > Main.cacheSize) {
					System.out.println(String.format("Messages: %s", Main.messages.size()));
					Main.messages.remove(0);
				}
			} catch (InterruptedException error) {
				break;
			}
		}
	}

	public void start() {
		if (thread == null) {
			thread = new Thread(this);
			thread.start();
		}
	}
}
