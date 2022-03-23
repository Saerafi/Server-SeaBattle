import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Arrays;
import java.util.Random;

// main class, starts the server and does a restart
class main {
    public static void main (String[] argv) throws Exception{
        while (true) {
            Server serv = new Server();
            serv.start();
            serv.join();
        }
    }
}


// class for player, his stats, ships, socket and game status
class Client {
    Socket cs;
    DataInputStream dis;
    DataOutputStream dos;
    int[][] field;
    int alive;

    public Client(Socket cs) throws Exception{
        this.cs = cs;
        dis = new DataInputStream(cs.getInputStream());
        dos = new DataOutputStream(cs.getOutputStream());
        alive = 20;
    }
}


// class for Server, this class provides the game, in-game management and connects two players
// this class works like a thread, hence it is more convenient to manage its work
class Server extends Thread {
    ServerSocket ss;
    Client client1;
    Client client2;

    public Server() throws Exception {
        ss = new ServerSocket(3128);
    }

    public void run() {
        try {

            // work of this class is separated in two parts: preparation and the game
            // this is the part of preparation where two players are connecting to the server and preparing their fields
            client1 = new Client(ss.accept());
            ClientWaitThread first = new ClientWaitThread(client1);
            first.start();
            System.out.println("First");
            client2 = new Client(ss.accept());
            ClientWaitThread second = new ClientWaitThread(client2);
            second.start();
            System.out.println("Second");
            first.join();
            second.join();
            System.out.println("Players are ready");
            client1.dos.writeUTF("start");
            client2.dos.writeUTF("start");

            // when both players are ready to play starts the second part
            // the game starts and ends here
            Game game = new Game(client1, client2);
            game.start();
            game.join();

        } catch (Exception ex) {}
    }
}


// this thread is made to prepare player for the game(placement of the ships)
class ClientWaitThread extends Thread {
    Client client;
    int FIELD_SIZE = 10;

    public ClientWaitThread(Client client) {
        this.client = client;
    }

    public void run() {
        try {
            String s = client.dis.readUTF();
            while (!s.equals("start")) {
                System.out.println(s);
                s = client.dis.readUTF();
            }
            System.out.println(s);
            fillField(client);
            client.dos.writeUTF("Field has been send\n");
            System.out.println("Field has been send");

        } catch (Exception ex) {}
    }

    //function that provides correct placement of user ships
    public void fillField(Client client) {
        Random random = new Random();
        int[][] field = new int[FIELD_SIZE][FIELD_SIZE];
        int[] ships = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};
        String list = "field:";

        for (int i = 0; i < 10; i++) {
            // start point of the ship and direction
            int x = random.nextInt(field.length);
            int y = random.nextInt(field.length);
            boolean vertical = random.nextBoolean();

            // correct start point so that the ship could fit in the field
            if (vertical) {
                if (y + ships[i] > FIELD_SIZE) {
                    y -= ships[i];
                }
            } else if (x + ships[i] > FIELD_SIZE) {
                x -= ships[i];
            }

            boolean isFree = true;
            // check for free space
            if (vertical) {
                for (int m = y; m < y + ships[i]; m++) {
                    if (field[m][x] != 0) {
                        isFree = false;
                        break;
                    }
                }
            } else {
                for (int n = x; n < x + ships[i]; n++) {
                    if (field[y][n] != 0) {
                        isFree = false;
                        break;
                    }
                }
            }

            // no free space found, retry
            if (!isFree) {
                i--;
                continue;
            }

            // fill in the adjacent cells
            if (vertical) {
                for (int m = Math.max(0, x - 1); m < Math.min(FIELD_SIZE, x + 2); m++) {
                    for (int n = Math.max(0, y - 1); n < Math.min(FIELD_SIZE, y + ships[i] + 1); n++) {
                        field[n][m] = 9;
                    }
                }
            } else {
                for (int m = Math.max(0, y - 1); m < Math.min(FIELD_SIZE, y + 2); m++) {
                    for (int n = Math.max(0, x - 1); n < Math.min(FIELD_SIZE, x + ships[i] + 1); n++) {
                        field[m][n] = 9;
                    }
                }
            }

            // fill in the ship cells
            for (int j = 0; j < ships[i]; j++) {
                field[y][x] = ships[i];
                list += y + ":";
                list += x + ":";
                if (vertical) {
                    y++;
                } else {
                    x++;
                }
            }
        }

        //
        for (int z = 0; z < 10; z++) {
            for (int x = 0; x < 10; x++) {
                if (field[z][x] == 9) {
                    field[z][x] = 0;
                }
                else if ((field[z][x] > 0) & (field[z][x] < 9)) {
                    field[z][x] = 1;
                }
            }
        }

        // build char map
        client.field = field;
        System.out.println(list);
        try {
            client.dos.writeUTF(list);
        } catch (Exception ex) {}


        char[][] map = new char[FIELD_SIZE][FIELD_SIZE];
        for (int i = 0; i < FIELD_SIZE; i++) {
            for (int j = 0; j < FIELD_SIZE; j++) {
                map[i][j] = field[i][j] == 0 || field[i][j] == 9 ? '.' : 'O';
            }
        }
        // print map
        Arrays.stream(map)
                .forEach(m -> System.out.println(Arrays.toString(m).replace(",", "")));
    }

}

// in this thread goes exactly the game cycle
class Game extends Thread {
    Client client1;
    Client client2;
    Boolean turn;

    Client attackingClient;
    Client victimClient;
    String[] input;

    public Game(Client client1, Client client2) {
        this.client1 = client1;
        this.client2 = client2;
        turn = new Random().nextBoolean();
    }

    public void run() {
        while ((client1.alive > 0) & (client2.alive > 0)) {
            try {
                if (turn) {
                    attackingClient = client1;
                    victimClient = client2;
                } else {
                    attackingClient = client2;
                    victimClient = client1;
                }

                attackingClient.dos.writeUTF("yourturn");
                victimClient.dos.writeUTF("enemyturn");

                String s = attackingClient.dis.readUTF();
                System.out.println(s);
                String[] input = s.trim().split(":");
                if (victimClient.field[Integer.parseInt(input[2])][Integer.parseInt(input[1])] == 1) {
                    victimClient.dos.writeUTF("shot:myfield:" + input[2] + ":" + input[1]);
                    attackingClient.dos.writeUTF("shot:enemyfield:" + input[2] + ":" + input[1]);
                    victimClient.alive--;
                } else {
                    victimClient.dos.writeUTF("miss:myfield:" + input[2] + ":" + input[1]);
                    attackingClient.dos.writeUTF("miss:enemyfield:" + input[2] + ":" + input[1]);
                    if (turn) {
                        turn = false;
                    } else {
                        turn = true;
                    }
                }
            } catch (Exception ex) {}
        }
        try {
            if (client1.alive < 0) {
                client1.dos.writeUTF("youlose");
                client2.dos.writeUTF("youwin");
            }
            else {
                client2.dos.writeUTF("youlose");
                client1.dos.writeUTF("youwin");
            }
        } catch (Exception ex) {}
    }

}