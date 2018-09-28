package org.openhab.binding.upb.handler;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.TooManyListenersException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.openhab.binding.upb.Constants;
import org.openhab.binding.upb.internal.MessageBuilder;
import org.openhab.binding.upb.internal.UPBMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.SerialPortEvent;
import gnu.io.SerialPortEventListener;
import gnu.io.UnsupportedCommOperationException;

public class SerialPIMHandler extends PIMHandler {
    private static final int SERIAL_RECEIVE_TIMEOUT = 100;
    private static final int WRITE_QUEUE_LENGTH = 128;
    private static final int ACK_TIMEOUT_MS = 500;

    private final Logger logger = LoggerFactory.getLogger(SerialPIMHandler.class);

    private volatile SerialPort serialPort;
    private volatile SerialIoThread receiveThread;

    public SerialPIMHandler(final Bridge bridge) {
        super(bridge);
    }

    @Override
    public void initialize() {
        super.initialize();
        logger.debug("Initializing Serial UPB PIM {}.", getThing().getUID());

        final String portId = (String) getConfig().get(Constants.CONFIGURATION_PORT);
        if (portId == null || portId.isEmpty()) {
            logger.error("serial port is not set");
            return;
        }

        super.initialize();
        try {
            serialPort = openSerialPort(portId);
        } catch (final Exception e) {
            throw new RuntimeException("failed to open serial port", e);
        }
        logger.debug("Starting receive thread");
        receiveThread = new SerialIoThread();
        receiveThread.setName("upb-serial-reader");

        // RXTX serial port library causes high CPU load
        // Start event listener, which will just sleep and slow down event loop
        try {
            serialPort.addEventListener(receiveThread);
        } catch (final TooManyListenersException e) {
            receiveThread.interrupt();
            throw new RuntimeException(e);
        }
        serialPort.notifyOnDataAvailable(true);

        // Once the receiver starts, it may set the PIM status to ONLINE
        // so we must ensure all initialization is finished at that point.
        receiveThread.start();
    }

    @Override
    public void dispose() {
        if (receiveThread != null) {
            receiveThread.interrupt();
            try {
                receiveThread.join(1000);
            } catch (final InterruptedException e) {
            }
            receiveThread = null;
        }
        if (serialPort != null) {
            logger.debug("Closing serial port");
            serialPort.close();
        }
        logger.info("Stopped UPB serial handler");
        super.dispose();
    }

    private SerialPort openSerialPort(String port) {
        logger.info("opening serial port {}", port);
        final CommPortIdentifier portId;
        try {
            portId = CommPortIdentifier.getPortIdentifier(port);
        } catch (final NoSuchPortException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    Constants.OFFLINE_SERIAL_EXISTS);
            throw new RuntimeException("Port does not exist", ex);
        }

        final SerialPort serialPort;
        try {
            serialPort = portId.open("org.openhab.binding.upb", 1000);
        } catch (final PortInUseException ex) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    Constants.OFFLINE_SERIAL_INUSE);
            throw new RuntimeException("Port is in use", ex);
        }
        try {
            serialPort.setSerialPortParams(4800, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
            serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
            serialPort.enableReceiveThreshold(1);
            serialPort.enableReceiveTimeout(SERIAL_RECEIVE_TIMEOUT);
        } catch (UnsupportedCommOperationException e) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.COMMUNICATION_ERROR,
                    Constants.OFFLINE_SERIAL_UNSUPPORTED);
            throw new RuntimeException("Failed to configure serial port");
        }
        logger.info("Serial port is initialized");
        return serialPort;
    }

    @Override
    public CompletionStage<CmdStatus> sendPacket(final MessageBuilder msg) {
        if (receiveThread != null) {
            return receiveThread.enqueue(msg);
        } else {
            return exceptionallyCompletedFuture(new IllegalStateException("I/O thread not active"));
        }
    }

    @Override
    public void deviceDiscovered(int node) {
        // TODO Auto-generated method stub
    }

    private class SerialIoThread extends Thread implements SerialPortEventListener {
        private final Logger logger = LoggerFactory.getLogger(SerialIoThread.class);
        private final byte[] ENABLE_MESSAGE_MODE_CMD = { 0x17, 0x70, 0x02, (byte) 0x8e, 0x0d };
        private final byte[] buffer = new byte[512];
        private int bufferLength = 0;
        private final ExecutorService writeExecutor = new ThreadPoolExecutor(1, 1, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(WRITE_QUEUE_LENGTH));
        private volatile WriteRunnable currentWrite = null;

        @Override
        public void serialEvent(final SerialPortEvent event) {
            try {
                logger.trace("RXTX library CPU load workaround, sleep forever");
                Thread.sleep(Long.MAX_VALUE);
            } catch (final InterruptedException e) {
                // ignore
            }
        }

        @Override
        public void run() {
            final byte[] buffer = new byte[256];
            try (final InputStream in = serialPort.getInputStream()) {
                enterMessageMode();
                while (!interrupted()) {
                    try {
                        for (int len = -1; (len = in.read(buffer)) >= 0;) {
                            addData(buffer, len);
                            if (interrupted()) {
                                break;
                            }
                        }
                    } catch (final IOException e) {
                        logger.error("Exception during receive, exiting thread", e);
                        break;
                    }
                }
            } catch (final Exception e) {
                logger.error("Exception in UPB read thread", e);
            } finally {
                shutdownAndAwaitTermination(writeExecutor);
                serialPort.removeEventListener();
            }
            logger.debug("UPB read thread stopped");
        }

        private void addData(final byte[] data, final int length) {
            if (bufferLength + length > buffer.length) {
                // buffer overflow, discard entire buffer
                bufferLength = 0;
            }
            System.arraycopy(data, 0, buffer, bufferLength, length);
            bufferLength += length;
            interpretBuffer();
        }

        private int findMessageLength(final byte[] buffer, final int bufferLength) {
            for (int i = 0; i < bufferLength; i++) {
                if (buffer[i] == 13) {
                    return i;
                }
            }
            return -1;
        }

        /**
         * Attempts to interpret any messages that may be contained in the buffer.
         */
        private void interpretBuffer() {
            int messageLength = findMessageLength(buffer, bufferLength);

            while (messageLength != -1) {
                final String message = new String(Arrays.copyOfRange(buffer, 0, messageLength), US_ASCII);
                logger.debug("UPB Message: {}", message);

                final int remainingBuffer = bufferLength - messageLength - 1;
                if (remainingBuffer > 0) {
                    System.arraycopy(buffer, messageLength + 1, buffer, 0, remainingBuffer);
                }
                bufferLength = remainingBuffer;
                handleMessage(UPBMessage.fromString(message));
                messageLength = findMessageLength(buffer, bufferLength);
            }
        }

        private void handleMessage(final UPBMessage msg) {
            updateStatus(ThingStatus.ONLINE);
            switch (msg.getType()) {
                case ACK:
                    if (currentWrite != null) {
                        currentWrite.ackReceived(true);
                    }
                    break;
                case NAK:
                    if (currentWrite != null) {
                        currentWrite.ackReceived(false);
                    }
                    break;
                case ACCEPT:
                    break;
                case ERROR:
                    logger.warn("received ERROR response from PIM");
                    break;
                default:
                    // ignore
            }
            incomingMessage(msg);
        }

        public CompletionStage<CmdStatus> enqueue(final MessageBuilder msg) {
            final CompletableFuture<CmdStatus> completion = new CompletableFuture<>();
            final Runnable task = new WriteRunnable(msg.build(), completion);
            try {
                writeExecutor.execute(task);
            } catch (final RejectedExecutionException e) {
                completion.completeExceptionally(e);
            }
            return completion;
        }

        // puts the PIM is in message mode
        private void enterMessageMode() {
            try {
                serialPort.getOutputStream().write(ENABLE_MESSAGE_MODE_CMD);
            } catch (final IOException e) {
                logger.error("error setting message mode", e);
            }
        }

        void shutdownAndAwaitTermination(final ExecutorService pool) {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                    if (!pool.awaitTermination(1, TimeUnit.SECONDS)) {
                        logger.error("executor did not terminate");
                    }
                }
            } catch (final InterruptedException ie) {
                pool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        private class WriteRunnable implements Runnable {
            private static final int MAX_RETRIES = 3;

            private final String msg;
            private final CompletableFuture<CmdStatus> completion;

            private Boolean ack = null;
            private volatile CountDownLatch ackLatch;

            public WriteRunnable(final String msg, final CompletableFuture<CmdStatus> completion) {
                this.msg = msg;
                this.completion = completion;
            }

            // called by reader thread if on ACK or NAK
            public void ackReceived(final boolean ack) {
                if (logger.isDebugEnabled()) {
                    if (ack) {
                        logger.debug("ACK received");
                    } else {
                        logger.debug("NAK received");
                    }
                }
                this.ack = ack;
                ackLatch.countDown();
            }

            @Override
            public void run() {
                currentWrite = this;
                try {
                    logger.debug("Writing bytes: {}", msg);
                    final OutputStream out = serialPort.getOutputStream();
                    for (int tries = 0; tries < MAX_RETRIES && ack == null; tries++) {
                        ackLatch = new CountDownLatch(1);
                        out.write(0x14);
                        out.write(msg.getBytes(US_ASCII));
                        out.write(0x0d);
                        final boolean acked = ackLatch.await(ACK_TIMEOUT_MS, MILLISECONDS);
                        if (acked) {
                            break;
                        }
                        logger.debug("ack timed out, retrying ({} of {})", tries + 1, MAX_RETRIES);
                    }
                    if (ack == null) {
                        logger.debug("write not acked");
                        completion.complete(CmdStatus.WRITE_FAILED);
                    } else if (ack) {
                        completion.complete(CmdStatus.ACK);
                    } else {
                        completion.complete(CmdStatus.NAK);
                    }
                } catch (final Exception e) {
                    logger.error("error writing message", e);
                    completion.complete(CmdStatus.WRITE_FAILED);
                }
            }
        }
    }

    /**
     * Returns a new {@code CompletableFuture} that is already exceptionally completed with
     * the given exception.
     *
     * @param throwable the exception
     * @param           <T> an arbitrary type for the returned future; can be anything since the future
     *                  will be exceptionally completed and thus there will never be a value of type
     *                  {@code T}
     * @return a future that exceptionally completed with the supplied exception
     * @throws NullPointerException if the supplied throwable is {@code null}
     * @since 0.1.0
     */
    public static <T> CompletableFuture<T> exceptionallyCompletedFuture(final Throwable throwable) {
        final CompletableFuture<T> future = new CompletableFuture<>();
        future.completeExceptionally(throwable);
        return future;
    }
}
