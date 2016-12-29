import java.awt.Button;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Scanner;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

public class Chip8 implements Runnable {
	private long elapsedTime = 0;
	private long prevTime = 0;

	boolean exit = false;

	private byte[] RAM = new byte[4096];//4 KiB ram
	private int[] V = new int[16];//registers
	private int I; //address register
	private volatile int DT, ST;//delay, sound timers
	private int PC;//program counter
	private int[] stack = new int[16];//stack (16 elements)
	private int SP;//stack pointer
	private volatile boolean[][] display = new boolean[128][64];//array for the display
	private volatile boolean[] keys = new boolean[16];
	private volatile boolean isPaused = false;

	private final static String[] names = { "15PUZZLE", "BLINKY", "BLITZ", "BRIX", "CONNECT4", "GUESS", "HIDDEN",
			"INVADERS", "KALEID", "MAZE", "MERLIN", "MISSILE", "PONG", "PONG2", "PUZZLE", "SYZYGY", "TANK", "TETRIS",
			"TICTAC", "UFO", "VBRIX", "VERS", "WIPEOFF", "OWN FILE" };
	private final static int[] sleepTimes = { 0, 7000, 10000, 40000, 0, 0, 0, 13000, 0, 0, 0, 60000, 17000, 17000, 0,
			2000, 15000, 15000, 0, 30000, 30000, 35000, 40000 };
	private int sleepTime = 0;

	private Thread keyGetter;
	private int keyCode = 0;

	private Clip clip;
	private AudioInputStream soundStream;


	public static void main(String[] args) {
		while (true) {
			Chip8 c8 = new Chip8();
			c8.run();
		}
	}

	public void choose() {
		frame.setVisible(false);
		reset();
		isPaused = true;
		JFrame chooser = new JFrame("Choose a game to play!");
		chooser.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		chooser.setSize(450, 280);
		chooser.setLayout(new GridLayout(0, 4));
		class AListener implements ActionListener {
			int n;

			AListener(int a) {
				super();
				n = a;
			}

			@Override
			public void actionPerformed(ActionEvent e) {
				if (n < 23) {
					loadInJAR(names[n]);
					sleepTime = sleepTimes[n];
				} else {
					JFileChooser c = new JFileChooser();
					c.setDialogTitle("Choose the file you want to run");
					int returnVal = c.showOpenDialog(c);
					if (returnVal == JFileChooser.APPROVE_OPTION) {
						load(c.getSelectedFile().getAbsolutePath());
					} else
						return;
				}
				chooser.dispose();
				isPaused = false;
				frame.setVisible(true);
			}
		}
		for (int i = 0; i < 24; i++) {
			JButton b = new JButton(names[i]);
			chooser.add(b);
			b.addActionListener(new AListener(i));
		}
		chooser.setResizable(true);
		chooser.setLocationRelativeTo(null);
		chooser.setVisible(true);
	}

	KeyListener keyboard = new KeyListener() {
		@Override
		public void keyPressed(KeyEvent e) {
			int code = e.getKeyCode();
			switch (code) {
			case KeyEvent.VK_X:
				keys[0] = true;
				keyCode = 0;
				break;
			case KeyEvent.VK_1:
				keys[1] = true;
				keyCode = 1;
				break;
			case KeyEvent.VK_2:
				keys[2] = true;
				keyCode = 2;
				break;
			case KeyEvent.VK_3:
				keys[3] = true;
				keyCode = 3;
				break;
			case KeyEvent.VK_Q:
				keys[4] = true;
				keyCode = 4;
				break;
			case KeyEvent.VK_W:
				keys[5] = true;
				keyCode = 5;
				break;
			case KeyEvent.VK_E:
				keys[6] = true;
				keyCode = 6;
				break;
			case KeyEvent.VK_A:
				keys[7] = true;
				keyCode = 7;
				break;
			case KeyEvent.VK_S:
				keys[8] = true;
				keyCode = 8;
				break;
			case KeyEvent.VK_D:
				keys[9] = true;
				keyCode = 9;
				break;
			case KeyEvent.VK_Y:
				keys[10] = true;
				keyCode = 10;
				break;
			case KeyEvent.VK_Z:
				keys[10] = true;
				keyCode = 10;
				break;
			case KeyEvent.VK_C:
				keys[11] = true;
				keyCode = 11;
				break;
			case KeyEvent.VK_4:
				keys[12] = true;
				keyCode = 12;
				break;
			case KeyEvent.VK_R:
				keys[13] = true;
				keyCode = 13;
				break;
			case KeyEvent.VK_F:
				keys[14] = true;
				keyCode = 14;
				break;
			case KeyEvent.VK_V:
				keys[15] = true;
				keyCode = 15;
				break;

			case KeyEvent.VK_ESCAPE:
				exit();
				break;
			}
			if (keyGetter != null) {
				if (keyGetter.isAlive()) {
					if (code == KeyEvent.VK_X || code == KeyEvent.VK_1 || code == KeyEvent.VK_2 || code == KeyEvent.VK_3
							|| code == KeyEvent.VK_Q || code == KeyEvent.VK_W || code == KeyEvent.VK_E
							|| code == KeyEvent.VK_A || code == KeyEvent.VK_S || code == KeyEvent.VK_D
							|| code == KeyEvent.VK_Y || code == KeyEvent.VK_Z || code == KeyEvent.VK_C
							|| code == KeyEvent.VK_4 || code == KeyEvent.VK_R || code == KeyEvent.VK_F
							|| code == KeyEvent.VK_V) {
						keyGetter.interrupt();
					}
				}
			}
		}

		@Override
		public void keyReleased(KeyEvent e) {
			switch (e.getKeyCode()) {
			case KeyEvent.VK_X:
				keys[0] = false;
				break;
			case KeyEvent.VK_1:
				keys[1] = false;
				break;
			case KeyEvent.VK_2:
				keys[2] = false;
				break;
			case KeyEvent.VK_3:
				keys[3] = false;
				break;
			case KeyEvent.VK_Q:
				keys[4] = false;
				break;
			case KeyEvent.VK_W:
				keys[5] = false;
				break;
			case KeyEvent.VK_E:
				keys[6] = false;
				break;
			case KeyEvent.VK_A:
				keys[7] = false;
				break;
			case KeyEvent.VK_S:
				keys[8] = false;
				break;
			case KeyEvent.VK_D:
				keys[9] = false;
				break;
			case KeyEvent.VK_Y:
				keys[10] = false;
				break;
			case KeyEvent.VK_Z:
				keys[10] = false;
				break;
			case KeyEvent.VK_C:
				keys[11] = false;
				break;
			case KeyEvent.VK_4:
				keys[12] = false;
				break;
			case KeyEvent.VK_R:
				keys[13] = false;
				break;
			case KeyEvent.VK_F:
				keys[14] = false;
				break;
			case KeyEvent.VK_V:
				keys[15] = false;
				break;
			}
		}

		@Override
		public void keyTyped(KeyEvent e) {
		}
	};

	private JFrame frame = null;

	public Chip8() {
		reset();
		frame = new JFrame("CHIP-8 INTERPRETER") {
			@Override
			public void paint(Graphics g) {
				BufferedImage img = new BufferedImage(64, 32, BufferedImage.TYPE_INT_RGB);
				Graphics g2 = img.getGraphics();
				for (int a = 0; a < 64; a++)
					for (int b = 0; b < 32; b++) {
						if (display[a][b]) {
							g2.setColor(Color.BLACK);
							g2.fillRect(a, b, 1, 1);
						} else {
							g2.setColor(Color.WHITE);
							g2.fillRect(a, b, 1, 1);
						}
					}
				getContentPane().getGraphics().drawImage(img, 0, 0, getContentPane().getWidth(),
						getContentPane().getHeight(), 0, 0, img.getWidth(), img.getHeight(), getContentPane());
				repaint();
			}
		};
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.getContentPane().setPreferredSize(new Dimension(1024, 512));
		frame.pack();
		frame.setResizable(true);
		frame.setLocationRelativeTo(null);
		frame.addKeyListener(keyboard);
		try {
			clip = AudioSystem.getClip();
		} catch (LineUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			soundStream = AudioSystem.getAudioInputStream(new BufferedInputStream(ClassLoader.getSystemClassLoader().getResourceAsStream("s.wav")));
		} catch (UnsupportedAudioFileException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			clip.open(soundStream);
		} catch (LineUnavailableException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		frame.setVisible(false);
		choose();
	}

	public void reset() {
		DT = 0;
		SP = 0;
		ST = 0; //first zero out the unsigned char registers
		I = 0; //then the other registers
		PC = 0x200;
		sleepTime = 0;

		for (short i = 0; i < 16; i++) //zero out registers
			V[i] = 0;
		for (short i = 0; i < 4096; i++) //the ram
			RAM[i] = 0;
		for (short i = 0; i < display.length; i++)
			for (short j = 0; j < display[0].length; j++) //and the display
				display[i][j] = false;
		for (short i = 0; i < 16; i++) //and the stack
			stack[i] = 0;
		for (short i = 0; i < 16; i++) //and the keys
			keys[i] = false;

		//fonts data:
		byte[] fontBuffer = { 0xF, 0x9, 0x9, 0x9, 0xF, 0x2, 0x6, 0x2, 0x2, 0x7, 0xF, 0x1, 0xF, 0x8, 0xF, 0xF, 0x1, 0xF,
				0x1, 0xF, 0x9, 0x9, 0xF, 0x1, 0x1, 0xF, 0x8, 0xF, 0x1, 0xF, 0xF, 0x8, 0xF, 0x9, 0xF, 0xF, 0x1, 0x2, 0x4,
				0x4, 0xF, 0x9, 0xF, 0x9, 0xF, 0xF, 0x9, 0xF, 0x1, 0xF, 0xF, 0x9, 0xF, 0x9, 0x9, 0xE, 0x9, 0xE, 0x9, 0xE,
				0xF, 0x8, 0x8, 0x8, 0xF, 0xE, 0x9, 0x9, 0x9, 0xE, 0xF, 0x8, 0xF, 0x8, 0xF, 0xF, 0x8, 0xF, 0x8, 0x8 };
		for (byte i = 0; i < 80; i++) {
			RAM[i] = (byte) (Byte.toUnsignedInt(fontBuffer[i]) * 16);
		}
	}

	public void exit() {
		exit = true;
		frame.dispose();
		if (keyGetter != null) {
			keyGetter.interrupt();
		}
		reset();
	}
	
	private synchronized void sound(){
        // Note: use .wav files   
		if(ST!=0){
			if(!clip.isRunning()){
	        clip.start();
		    }
		}else{
			clip.stop();
		}
    }

	public void run() {
		while (!exit) {
			if (!isPaused) {
				sound();
				operate(Byte.toUnsignedInt(RAM[PC]) * 256 + Byte.toUnsignedInt(RAM[PC + 1]));//operate current intruction
				long current = System.nanoTime();
				elapsedTime += (current - prevTime);
				prevTime = current;
				if (elapsedTime >= 16666667L) {
					if (DT != 0)
						DT--;
					if (ST != 0)
						ST--;
					elapsedTime = 0;
				}

				try {
					Thread.sleep(0, sleepTime);
				} catch (Exception e) {
					System.out.println(e.getMessage());
				}
			}
		}
		clip.close();
	}

	public void load(String filename) {
		byte[] buffer = null;
		File a_file = new File(filename);
		try {
			FileInputStream fis = new FileInputStream(filename);
			int length = (int) a_file.length();
			buffer = new byte[length];
			fis.read(buffer);
			fis.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		System.arraycopy(buffer, 0, RAM, 0x200, buffer.length);
	}

	private void loadInJAR(String filename) {
		byte[] buffer = new byte[4096];

		try {
			ClassLoader.getSystemClassLoader().getResourceAsStream(filename).read(buffer);
			//getClass().getClassLoader().getResourceAsStream("/"+filename).read(buffer);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		System.arraycopy(buffer, 0, RAM, 0x200, buffer.length - 0x200);
	}

	private void operate(int opcode) {
		int n = opcode % 0x10;
		int nnn = opcode % 0x1000;
		int kk = opcode % 0x100;
		int x = nnn / 0x100, y = kk / 0x10;

		switch (opcode / 0x1000) {
		case 0x0:
			switch (nnn) {
			case 0x0E0:
				for (short i = 0; i < display.length; i++)
					for (short j = 0; j < display[0].length; j++)
						display[i][j] = false; //clear display
				PC += 2;
				break;
			case 0x0EE:
				SP--;
				PC = stack[SP] + 2; //return from a subroutine
				break;
			}
			break;
		case 0x1:
			PC = nnn; //jump to address nnn (0x1nnn)
			break;
		case 0x2:
			stack[SP] = PC; //call a subroutine at nnn (0x2nnn)
			SP++;
			PC = nnn;
			break;
		case 0x3:
			if (kk == V[x])
				PC += 2; //if Vx and kk equals, skip next instruction (0x3xkk)
			PC += 2;
			break;
		case 0x4:
			if (kk != V[x])
				PC += 2; //if Vx and kk not equals, skip next instruction (0x4xkk)
			PC += 2;
			break;
		case 0x5:
			if (V[y] == V[x])
				PC += 2; //if Vx and Vy equals, skip next instruction (0x5xy0)
			PC += 2;
			break;
		case 0x6:
			//set Vx to kk
			V[x] = kk;
			PC += 2;
			break;
		case 0x7:
			//set Vx = Vx + kk
			V[x] = (V[x] + kk) % 256;
			PC += 2;
			break;
		case 0x8:
			switch (n) {
			case 0x0:
				//set Vx = Vy
				V[x] = V[y];
				PC += 2;
				break;
			case 0x1:
				//set Vx = Vx OR Vy
				V[x] = V[x] | V[y];
				PC += 2;
				break;
			case 0x2:
				//set Vx = Vx AND Vy
				V[x] = V[x] & V[y];
				PC += 2;
				break;
			case 0x3:
				//set Vx = Vx XOR Vy
				V[x] = V[x] ^ V[y];
				PC += 2;
				break;
			case 0x4:
				//set Vx = Vx + Vy, set VF = carry
				if ((V[x] + V[y]) > 255)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = (V[x] + V[y]) % 256;
				PC += 2;
				break;
			case 0x5:
				//set Vx = Vx - Vy, set VF = NOT borrow
				if (V[x] > V[y])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = V[x] - V[y];
				if (V[x] < 0)
					V[x] = -V[x];
				PC += 2;
				break;
			case 0x6:
				//bitwise right shift on Vx by 1, set VF = carry
				if ((V[x] % 2) == 1)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] /= 2;
				PC += 2;
				break;
			case 0x7:
				//set Vx = Vy - Vx, set VF = NOT borrow
				if (V[y] > V[x])
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] = V[y] - V[x];
				if (V[x] < 0)
					V[x] = -V[x];
				PC += 2;
				break;
			case 0xE:
				//bitwise left shift on Vx by 1, set VF = carry
				if ((V[x] / 0x80) == 1)
					V[0xF] = 1;
				else
					V[0xF] = 0;
				V[x] *= 2;
				PC += 2;
				break;
			}
			break;
		case 0x9:
			//skip next instruction if Vx != Vy
			if (V[x] != V[y])
				PC += 2;
			PC += 2;
			break;
		case 0xA:
			//set I = nnn
			I = nnn;
			PC += 2;
			break;
		case 0xB:
			//jump to location nnn + V0
			PC = nnn + V[0];
			break;
		case 0xC:
			//set Vx = random byte AND kk
			V[x] = (int) (Math.floor((Math.random() * 256))) & kk;
			PC += 2;
			break;
		case 0xD:
			//display n-byte sprite starting at memory location I at (Vx, Vy), set VF = collision
			boolean collised = false;

			for (int i = 0; i < n; i++) {
				int row = Byte.toUnsignedInt(RAM[I + i]);
				boolean rowBool[] = new boolean[8];

				for (int j = 0; j < 8; j++)
					rowBool[j] = ((row / (int) (Math.pow(2, (7 - j)))) % 2 == 1);

				for (int j = 0; j < 8; j++) {
					int absX;
					int absY;

					absX = (V[x] + j) % 64;
					absY = (V[y] + i) % 32;

					if (display[absX][absY] && rowBool[j])
						collised = true;
					if (rowBool[j])
						display[absX][absY] = !display[absX][absY];
				}
			}
			if (collised)
				V[0xF] = 1;
			else
				V[0xF] = 0;
			PC += 2;
			break;
		case 0xE:
			switch (kk) {
			case 0x9E:
				//skip next instuction if the key corresponding to the value of Vx is currently in the down position (pressed)
				if (keys[V[x]])
					PC += 2;
				PC += 2;
				break;
			case 0xA1:
				//skip next instuction if the key corresponding to the value of Vx is currently in the up position (not pressed)
				if (!keys[V[x]])
					PC += 2;
				PC += 2;
				break;
			}
			break;
		case 0xF:
			switch (kk) {
			case 0x07:
				//set Vx = delay timer value.
				V[x] = DT;
				PC += 2;
				break;
			case 0x0A:
				//wait for a key press, store the value of the key in Vx
				Runnable getKey = new Runnable() {
					@Override
					public void run() {
						thloop: while (true)
							if (Thread.interrupted())
								break thloop;
					}
				};
				keyGetter = new Thread(getKey);
				keyGetter.start();
				try {
					keyGetter.join();
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				keyGetter = null;
				V[x] = keyCode;
				PC += 2;
				break;
			case 0x15:
				//set delay timer = Vx
				DT = V[x];
				PC += 2;
				break;
			case 0x18:
				//set sound timer = Vx
				ST = V[x];
				PC += 2;
				break;
			case 0x1E:
				//set I = I + Vx
				I += V[x];
				PC += 2;
				break;
			case 0x29:
				//set I = location of sprite for hex character Vx
				if (V[x] < 16)
					I = 5 * V[x];
				PC += 2;
				break;
			case 0x30:
				//set I = location of sprite for digit Vx 10 byte
				if (V[x] < 10)
					I = 10 * V[x] + 80;
				PC += 2;
				break;
			case 0x33:
				//store BCD representation of Vx in memory locations I, I+1, and I+2
				int tr;
				tr = ((V[x] / 100) % 0x10);
				RAM[I] = (byte) tr;
				tr = ((V[x] % 100) / 10);
				RAM[I + 1] = (byte) tr;
				tr = (V[x] % 10);
				RAM[I + 2] = (byte) tr;
				PC += 2;
				break;
			case 0x55:
				//store registers V0 through Vx in memory starting at location I
				for (int k = 0; k <= x; k++) {
					RAM[I + k] = (byte) (V[k]);
				}
				PC += 2;
				break;
			case 0x65:
				//read registers V0 through Vx from memory starting at location I
				for (int k = 0; k <= x; k++) {
					V[k] = Byte.toUnsignedInt(RAM[I + k]);
				}
				PC += 2;
				break;
			}
			break;
		}
	}
}
