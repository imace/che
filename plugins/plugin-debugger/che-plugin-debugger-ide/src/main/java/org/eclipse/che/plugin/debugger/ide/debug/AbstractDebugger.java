/*******************************************************************************
 * Copyright (c) 2012-2016 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.plugin.debugger.ide.debug;

import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.web.bindery.event.shared.EventBus;

import org.eclipse.che.api.debug.shared.dto.LinePositionDto;
import org.eclipse.che.api.debug.shared.dto.SimpleValueDto;
import org.eclipse.che.api.debug.shared.model.SimpleValue;
import org.eclipse.che.ide.api.app.AppContext;
import org.eclipse.che.ide.api.app.CurrentProject;
import org.eclipse.che.ide.api.editor.EditorAgent;
import org.eclipse.che.ide.api.editor.text.LinearRange;
import org.eclipse.che.ide.api.editor.texteditor.TextEditor;
import org.eclipse.che.ide.api.machine.events.WsAgentStateEvent;
import org.eclipse.che.ide.api.machine.events.WsAgentStateHandler;
import org.eclipse.che.api.debug.shared.dto.BreakpointDto;
import org.eclipse.che.api.debug.shared.dto.DebugSessionDto;
import org.eclipse.che.api.debug.shared.dto.LocationDto;
import org.eclipse.che.api.debug.shared.dto.StackFrameDumpDto;
import org.eclipse.che.api.debug.shared.dto.VariableDto;
import org.eclipse.che.api.debug.shared.dto.VariablePathDto;
import org.eclipse.che.api.debug.shared.dto.action.ResumeActionDto;
import org.eclipse.che.api.debug.shared.dto.action.StartActionDto;
import org.eclipse.che.api.debug.shared.dto.action.StepIntoActionDto;
import org.eclipse.che.api.debug.shared.dto.action.StepOutActionDto;
import org.eclipse.che.api.debug.shared.dto.action.StepOverActionDto;
import org.eclipse.che.api.debug.shared.dto.event.BreakpointActivatedEventDto;
import org.eclipse.che.api.debug.shared.dto.event.DebuggerEventDto;
import org.eclipse.che.api.debug.shared.dto.event.SuspendEventDto;
import org.eclipse.che.api.debug.shared.model.DebuggerInfo;
import org.eclipse.che.api.debug.shared.model.Location;
import org.eclipse.che.api.debug.shared.model.StackFrameDump;
import org.eclipse.che.api.debug.shared.model.Variable;
import org.eclipse.che.api.debug.shared.model.VariablePath;
import org.eclipse.che.api.debug.shared.model.action.Action;
import org.eclipse.che.api.debug.shared.model.impl.StackFrameDumpImpl;
import org.eclipse.che.api.debug.shared.model.impl.SimpleValueImpl;
import org.eclipse.che.api.promises.client.Function;
import org.eclipse.che.api.promises.client.FunctionException;
import org.eclipse.che.api.promises.client.Operation;
import org.eclipse.che.api.promises.client.OperationException;
import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseError;
import org.eclipse.che.api.promises.client.js.JsPromise;
import org.eclipse.che.api.promises.client.js.JsPromiseError;
import org.eclipse.che.api.promises.client.js.Promises;
import org.eclipse.che.commons.annotation.Nullable;
import org.eclipse.che.ide.api.debug.Breakpoint;
import org.eclipse.che.ide.api.debug.DebuggerServiceClient;
import org.eclipse.che.ide.api.project.tree.VirtualFile;
import org.eclipse.che.ide.debug.Debugger;
import org.eclipse.che.ide.debug.DebuggerDescriptor;
import org.eclipse.che.ide.debug.DebuggerManager;
import org.eclipse.che.ide.debug.DebuggerObservable;
import org.eclipse.che.ide.debug.DebuggerObserver;
import org.eclipse.che.ide.dto.DtoFactory;
import org.eclipse.che.ide.resource.Path;
import org.eclipse.che.ide.rest.HTTPStatus;
import org.eclipse.che.ide.util.loging.Log;
import org.eclipse.che.ide.util.storage.LocalStorage;
import org.eclipse.che.ide.util.storage.LocalStorageProvider;
import org.eclipse.che.ide.websocket.MessageBus;
import org.eclipse.che.ide.websocket.MessageBusProvider;
import org.eclipse.che.ide.websocket.WebSocketException;
import org.eclipse.che.ide.websocket.rest.SubscriptionHandler;
import org.eclipse.che.ide.websocket.rest.exceptions.ServerException;

import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The common debugger.
 *
 * @author Anatoliy Bazko
 */
public abstract class AbstractDebugger implements Debugger, DebuggerObservable {

    public static final String LOCAL_STORAGE_DEBUGGER_SESSION_KEY = "che-debugger-session";
    public static final String LOCAL_STORAGE_DEBUGGER_STATE_KEY   = "che-debugger-state";

    protected final DtoFactory dtoFactory;
    protected final AppContext appContext;

    private final List<DebuggerObserver> observers;
    private final DebuggerServiceClient  service;
    private final LocalStorageProvider   localStorageProvider;
    private final EventBus               eventBus;
    private final ActiveFileHandler      activeFileHandler;
    private final DebuggerManager        debuggerManager;
    private final String                 debuggerType;
    private final String                 eventChannel;
    private final EditorAgent            editorAgent;

    private DebugSessionDto                       debugSessionDto;
    private Location                              currentLocation;
    private SubscriptionHandler<DebuggerEventDto> debuggerEventsHandler;

    private MessageBus messageBus;

    public AbstractDebugger(DebuggerServiceClient service,
                            DtoFactory dtoFactory,
                            LocalStorageProvider localStorageProvider,
                            MessageBusProvider messageBusProvider,
                            EventBus eventBus,
                            ActiveFileHandler activeFileHandler,
                            DebuggerManager debuggerManager,
                            String type,
                            AppContext appContext,
                            EditorAgent editorAgent) {
        this.service = service;
        this.dtoFactory = dtoFactory;
        this.localStorageProvider = localStorageProvider;
        this.eventBus = eventBus;
        this.activeFileHandler = activeFileHandler;
        this.debuggerManager = debuggerManager;
        this.observers = new ArrayList<>();
        this.debuggerType = type;
        this.appContext = appContext;
        this.editorAgent = editorAgent;
        this.eventChannel = debuggerType + ":events:";

        restoreDebuggerState();
        addHandlers(messageBusProvider);
    }


    private void addHandlers(final MessageBusProvider messageBusProvider) {
        eventBus.addHandler(WsAgentStateEvent.TYPE, new WsAgentStateHandler() {
            @Override
            public void onWsAgentStarted(WsAgentStateEvent event) {
                messageBus = messageBusProvider.getMachineMessageBus();

                if (isConnected()) {
                    Promise<DebugSessionDto> promise = service.getSessionInfo(debugSessionDto.getId());
                    promise.then(new Operation<DebugSessionDto>() {
                        @Override
                        public void apply(DebugSessionDto arg) throws OperationException {
                            debuggerManager.setActiveDebugger(AbstractDebugger.this);
                            setDebugSession(arg);

                            DebuggerInfo debuggerInfo = arg.getDebuggerInfo();
                            String info = debuggerInfo.getName() + " " + debuggerInfo.getVersion();
                            String address = debuggerInfo.getHost() + ":" + debuggerInfo.getPort();
                            DebuggerDescriptor debuggerDescriptor = new DebuggerDescriptor(info, address);
                            JsPromise<Void> promise = Promises.resolve(null);

                            for (DebuggerObserver observer : observers) {
                                observer.onDebuggerAttached(debuggerDescriptor, promise);
                            }

                            startCheckingEvents();
                        }
                    }).catchError(new Operation<PromiseError>() {
                        @Override
                        public void apply(PromiseError arg) throws OperationException {
                            invalidateDebugSession();
                            preserveDebuggerState();
                        }
                    });
                }
            }

            @Override
            public void onWsAgentStopped(WsAgentStateEvent event) {}
        });

        this.debuggerEventsHandler = new SubscriptionHandler<DebuggerEventDto>(new DebuggerEventUnmarshaller(dtoFactory)) {
            @Override
            public void onMessageReceived(DebuggerEventDto result) {
                if (isConnected()) {
                    onEventListReceived(result);
                }
            }

            @Override
            public void onErrorReceived(Throwable exception) {
                if (isConnected()) {
                    try {
                        messageBus.unsubscribe(eventChannel, this);
                    } catch (WebSocketException e) {
                        Log.error(AbstractDebugger.class, e);
                    }

                    if (exception instanceof ServerException) {
                        ServerException serverException = (ServerException)exception;
                        if (HTTPStatus.INTERNAL_ERROR == serverException.getHTTPStatus()
                            && serverException.getMessage() != null
                            && serverException.getMessage().contains("not found")) {

                            disconnect();
                        }
                    }
                }
            }
        };
    }

    private void onEventListReceived(@NotNull DebuggerEventDto event) {
        LocationDto newLocationDto;

        switch (event.getType()) {
            case SUSPEND:
                newLocationDto = ((SuspendEventDto)event).getLocation();
                break;
            case BREAKPOINT_ACTIVATED:
                BreakpointDto breakpointDto = ((BreakpointActivatedEventDto)event).getBreakpoint();
                onBreakpointActivated(breakpointDto.getLocation());
                return;
            case DISCONNECT:
                disconnect();
                return;
            default:
                Log.error(AbstractDebugger.class, "Unknown debuggerType of debugger event: " + event.getType());
                return;
        }

        if (newLocationDto != null) {
            currentLocation = newLocationDto;
            openCurrentFile();
        }

        preserveDebuggerState();
    }

    private void openCurrentFile() {
        activeFileHandler.openFile(currentLocation,
                                   new AsyncCallback<VirtualFile>() {
                                       @Override
                                       public void onFailure(Throwable caught) {
                                           for (DebuggerObserver observer : observers) {
                                               observer.onBreakpointStopped(currentLocation.getTarget(),
                                                                            currentLocation.getTarget(),
                                                                            currentLocation.getLineNumber());
                                           }
                                       }

                                       @Override
                                       public void onSuccess(VirtualFile result) {
                                           for (DebuggerObserver observer : observers) {
                                               observer.onBreakpointStopped(result.getPath(),
                                                                            currentLocation.getTarget(),
                                                                            currentLocation.getLineNumber());
                                           }
                                       }
                                   });
    }

    /**
     * Breakpoint became active. It might happens because of different reasons:
     * <li>breakpoint was deferred and VM eventually loaded class and added it</li>
     * <li>condition triggered</li>
     * <li>etc</li>
     */
    private void onBreakpointActivated(LocationDto locationDto) {
        for (DebuggerObserver observer : observers) {
            observer.onBreakpointActivated(locationDto.getTarget(), locationDto.getLineNumber() - 1);
        }
    }

    private void startCheckingEvents() {
        try {
            messageBus.subscribe(eventChannel, debuggerEventsHandler);
        } catch (WebSocketException e) {
            Log.error(DebuggerPresenter.class, e);
        }
    }

    private void stopCheckingDebugEvents() {
        try {
            if (messageBus.isHandlerSubscribed(debuggerEventsHandler, eventChannel)) {
                messageBus.unsubscribe(eventChannel, debuggerEventsHandler);
            }
        } catch (WebSocketException e) {
            Log.error(AbstractDebugger.class, e);
        }
    }

    @Override
    public Promise<SimpleValue> getValue(Variable variable) {
        if (!isConnected()) {
            return Promises.reject(JsPromiseError.create("Debugger is not connected"));
        }

        Promise<SimpleValueDto> promise = service.getValue(debugSessionDto.getId(), asDto(variable));
        return promise.then(new Function<SimpleValueDto, SimpleValue>() {
            @Override
            public SimpleValue apply(SimpleValueDto arg) throws FunctionException {
                return new SimpleValueImpl(arg);
            }
        });
    }

    @Override
    public Promise<StackFrameDump> dumpStackFrame() {
        if (!isConnected()) {
            return Promises.reject(JsPromiseError.create("Debugger is not connected"));
        }

        Promise<StackFrameDumpDto> stackFrameDump = service.getStackFrameDump(debugSessionDto.getId());
        return stackFrameDump.then(new Function<StackFrameDumpDto, StackFrameDump>() {
            @Override
            public StackFrameDump apply(StackFrameDumpDto arg) throws FunctionException {
                return new StackFrameDumpImpl(arg);
            }
        });
    }

    @Override
    public void addBreakpoint(final VirtualFile file, final int lineNumber) {
        if (isConnected()) {
            String fqn = pathToFqn(file);
            if (fqn == null) {
                return;
            }

            BreakpointDto breakpointDto = createBreakpoint(file, lineNumber, fqn);
            Promise<Void> promise = service.addBreakpoint(debugSessionDto.getId(), breakpointDto);
            promise.then(new Operation<Void>() {
                @Override
                public void apply(Void arg) throws OperationException {
                    Breakpoint breakpoint = new Breakpoint(Breakpoint.Type.BREAKPOINT, lineNumber, file.getPath(), file, true);
                    for (DebuggerObserver observer : observers) {
                        observer.onBreakpointAdded(breakpoint);
                    }
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getMessage());
                }
            });
        } else {
            Breakpoint breakpoint = new Breakpoint(Breakpoint.Type.BREAKPOINT, lineNumber, file.getPath(), file, false);
            for (DebuggerObserver observer : observers) {
                observer.onBreakpointAdded(breakpoint);
            }
        }
    }

    private BreakpointDto createBreakpoint(VirtualFile file, int lineNumber, String target) {
        CurrentProject currentProject = appContext.getCurrentProject();
        String projectPath = appContext.getCurrentProject().getRootProject().getPath();
        if (currentProject != null) {
            projectPath = appContext.getCurrentProject().getRootProject().getPath();
        }

        LocationDto locationDto = dtoFactory.createDto(LocationDto.class).withLineNumber(lineNumber + 1)
                                                                         .withTarget(target)
                                                                         .withProjectPath(projectPath)
                                                                         .withLinePosition(getLinePosition(file, lineNumber));
        return dtoFactory.createDto(BreakpointDto.class).withLocation(locationDto).withEnabled(true);
    }

    private LinePositionDto getLinePosition(VirtualFile file, int lineNumber) {
        TextEditor textEditor = (TextEditor)editorAgent.getOpenedEditor(Path.valueOf(file.getPath()));

        LinearRange range = textEditor.getDocument().getLinearRangeForLine(lineNumber);
        int startCharacterCoordinate = range.getStartOffset();
        int endCharacterCoordinate = range.getLength() + startCharacterCoordinate;

        return dtoFactory.createDto(LinePositionDto.class).withStartCharOffset(startCharacterCoordinate)
                                                          .withEndCharOffset(endCharacterCoordinate);
    }

    @Override
    public void deleteBreakpoint(final VirtualFile file, final int lineNumber) {
        if (isConnected()) {
            LocationDto locationDto = dtoFactory.createDto(LocationDto.class);
            locationDto.setLineNumber(lineNumber + 1);

            String fqn = pathToFqn(file);
            if (fqn == null) {
                return;
            }
            locationDto.setTarget(fqn);

            Promise<Void> promise = service.deleteBreakpoint(debugSessionDto.getId(), locationDto);
            promise.then(new Operation<Void>() {
                @Override
                public void apply(Void arg) throws OperationException {
                    for (DebuggerObserver observer : observers) {
                        Breakpoint breakpoint = new Breakpoint(Breakpoint.Type.BREAKPOINT, lineNumber, file.getPath(), file, false);
                        observer.onBreakpointDeleted(breakpoint);
                    }
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getMessage());
                }
            });
        }
    }

    @Override
    public void deleteAllBreakpoints() {
        if (isConnected()) {
            Promise<Void> promise = service.deleteAllBreakpoints(debugSessionDto.getId());

            promise.then(new Operation<Void>() {
                @Override
                public void apply(Void arg) throws OperationException {
                    for (DebuggerObserver observer : observers) {
                        observer.onAllBreakpointsDeleted();
                    }
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getMessage());
                }
            });
        }
    }

    @Override
    public Promise<Void> connect(Map<String, String> connectionProperties) {
        if (isConnected()) {
            return Promises.reject(JsPromiseError.create("Debugger already connected"));
        }

        Promise<DebugSessionDto> connect = service.connect(debuggerType, connectionProperties);
        final DebuggerDescriptor debuggerDescriptor = toDescriptor(connectionProperties);

        Promise<Void> promise = connect.then(new Function<DebugSessionDto, Void>() {
            @Override
            public Void apply(final DebugSessionDto arg) throws FunctionException {
                DebuggerInfo debuggerInfo = arg.getDebuggerInfo();
                debuggerDescriptor.setInfo(debuggerInfo.getName() + " " + debuggerInfo.getVersion());

                setDebugSession(arg);
                preserveDebuggerState();
                startCheckingEvents();
                startDebuggerWithDelay(arg);

                return null;
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                Log.error(AbstractDebugger.class, arg.getMessage());
                throw new OperationException(arg.getCause());
            }
        });

        for (DebuggerObserver observer : observers) {
            observer.onDebuggerAttached(debuggerDescriptor, promise);
        }

        return promise;
    }

    protected void startDebuggerWithDelay(final DebugSessionDto debugSessionDto) {
        new Timer() {
            @Override
            public void run() {
                StartActionDto action = dtoFactory.createDto(StartActionDto.class).withType(Action.TYPE.START);
                service.start(debugSessionDto.getId(), action);
            }
        }.schedule(2000);
    }

    @Override
    public void disconnect() {
        stopCheckingDebugEvents();

        Promise<Void> disconnect;
        if (isConnected()) {
            disconnect = service.disconnect(debugSessionDto.getId());
        } else {
            disconnect = Promises.resolve(null);
        }

        invalidateDebugSession();
        preserveDebuggerState();

        disconnect.then(new Operation<Void>() {
            @Override
            public void apply(Void arg) throws OperationException {
                for (DebuggerObserver observer : observers) {
                    observer.onDebuggerDisconnected();
                }
                debuggerManager.setActiveDebugger(null);
            }
        }).catchError(new Operation<PromiseError>() {
            @Override
            public void apply(PromiseError arg) throws OperationException {
                for (DebuggerObserver observer : observers) {
                    observer.onDebuggerDisconnected();
                }
                debuggerManager.setActiveDebugger(null);
            }
        });
    }

    @Override
    public void stepInto() {
        if (isConnected()) {
            for (DebuggerObserver observer : observers) {
                observer.onPreStepInto();
            }
            currentLocation = null;

            StepIntoActionDto action = dtoFactory.createDto(StepIntoActionDto.class);
            action.setType(Action.TYPE.STEP_INTO);

            Promise<Void> promise = service.stepInto(debugSessionDto.getId(), action);
            promise.catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getCause());
                }
            });
        }
    }

    @Override
    public void stepOver() {
        if (isConnected()) {
            for (DebuggerObserver observer : observers) {
                observer.onPreStepOver();
            }
            currentLocation = null;

            StepOverActionDto action = dtoFactory.createDto(StepOverActionDto.class);
            action.setType(Action.TYPE.STEP_OVER);

            Promise<Void> promise = service.stepOver(debugSessionDto.getId(), action);
            promise.catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getCause());
                }
            });
        }
    }

    @Override
    public void stepOut() {
        if (isConnected()) {
            for (DebuggerObserver observer : observers) {
                observer.onPreStepOut();
            }
            currentLocation = null;

            StepOutActionDto action = dtoFactory.createDto(StepOutActionDto.class);
            action.setType(Action.TYPE.STEP_OUT);

            Promise<Void> promise = service.stepOut(debugSessionDto.getId(), action);
            promise.catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getCause());
                }
            });
        }
    }

    @Override
    public void resume() {
        if (isConnected()) {
            for (DebuggerObserver observer : observers) {
                observer.onPreResume();
            }
            currentLocation = null;

            ResumeActionDto action = dtoFactory.createDto(ResumeActionDto.class);
            action.setType(Action.TYPE.RESUME);

            Promise<Void> promise = service.resume(debugSessionDto.getId(), action);
            promise.catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getCause());
                }
            });
        }
    }

    @Override
    public Promise<String> evaluate(String expression) {
        if (isConnected()) {
            return service.evaluate(debugSessionDto.getId(), expression);
        }

        return Promises.reject(JsPromiseError.create("Debugger is not connected"));
    }

    @Override
    public void setValue(final Variable variable) {
        if (isConnected()) {
            Promise<Void> promise = service.setValue(debugSessionDto.getId(), asDto(variable));

            promise.then(new Operation<Void>() {
                @Override
                public void apply(Void arg) throws OperationException {
                    for (DebuggerObserver observer : observers) {
                        observer.onValueChanged(variable.getVariablePath().getPath(), variable.getValue());
                    }
                }
            }).catchError(new Operation<PromiseError>() {
                @Override
                public void apply(PromiseError arg) throws OperationException {
                    Log.error(AbstractDebugger.class, arg.getMessage());
                }
            });
        }
    }

    @Override
    public boolean isConnected() {
        return debugSessionDto != null;
    }

    @Override
    public boolean isSuspended() {
        return isConnected() && currentLocation != null;
    }

    public String getDebuggerType() {
        return debuggerType;
    }

    @Override
    public void addObserver(DebuggerObserver observer) {
        observers.add(observer);
    }

    @Override
    public void removeObserver(DebuggerObserver observer) {
        observers.remove(observer);
    }

    protected void setDebugSession(DebugSessionDto debugSessionDto) {
        this.debugSessionDto = debugSessionDto;
    }

    private void invalidateDebugSession() {
        this.debugSessionDto = null;
        this.currentLocation = null;
    }

    /**
     * Preserves debugger information into the local storage.
     */
    protected void preserveDebuggerState() {
        LocalStorage localStorage = localStorageProvider.get();

        if (localStorage == null) {
            return;
        }

        if (!isConnected()) {
            localStorage.setItem(LOCAL_STORAGE_DEBUGGER_SESSION_KEY, "");
            localStorage.setItem(LOCAL_STORAGE_DEBUGGER_STATE_KEY, "");
        } else {
            localStorage.setItem(LOCAL_STORAGE_DEBUGGER_SESSION_KEY, dtoFactory.toJson(debugSessionDto));
            if (currentLocation == null) {
                localStorage.setItem(LOCAL_STORAGE_DEBUGGER_STATE_KEY, "");
            } else {
                localStorage.setItem(LOCAL_STORAGE_DEBUGGER_STATE_KEY, dtoFactory.toJson(currentLocation));
            }
        }
    }

    /**
     * Loads debugger information from the local storage.
     */
    protected void restoreDebuggerState() {
        invalidateDebugSession();

        LocalStorage localStorage = localStorageProvider.get();
        if (localStorage == null) {
            return;
        }

        String data = localStorage.getItem(LOCAL_STORAGE_DEBUGGER_SESSION_KEY);
        if (data != null && !data.isEmpty()) {
            DebugSessionDto debugSessionDto = dtoFactory.createDtoFromJson(data, DebugSessionDto.class);
            if (!debugSessionDto.getType().equals(getDebuggerType())) {
                return;
            }

            setDebugSession(debugSessionDto);
        }

        data = localStorage.getItem(LOCAL_STORAGE_DEBUGGER_STATE_KEY);
        if (data != null && !data.isEmpty()) {
            currentLocation = dtoFactory.createDtoFromJson(data, LocationDto.class);
        }
    }

    private VariableDto asDto(Variable variable) {
        VariableDto dto = dtoFactory.createDto(VariableDto.class);
        dto.withValue(variable.getValue());
        dto.withVariablePath(asDto(variable.getVariablePath()));
        dto.withPrimitive(variable.isPrimitive());
        dto.withType(variable.getType());
        dto.withName(variable.getName());
        dto.withExistInformation(variable.isExistInformation());
        dto.withVariables(asDto(variable.getVariables()));
        return dto;
    }

    private List<VariableDto> asDto(List<? extends Variable> variables) {
        List<VariableDto> dtos = new ArrayList<>(variables.size());
        for (Variable v : variables) {
            dtos.add(asDto(v));
        }
        return dtos;
    }

    private VariablePathDto asDto(VariablePath variablePath) {
        return dtoFactory.createDto(VariablePathDto.class).withPath(variablePath.getPath());
    }

    /**
     * Transforms file path to FQN>
     */
    @Nullable
    abstract protected String pathToFqn(VirtualFile file);//strange name for generic debugger

    abstract protected DebuggerDescriptor toDescriptor(Map<String, String> connectionProperties);
}
