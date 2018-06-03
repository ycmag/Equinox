/*
 * Copyright 2018 Murat Artim (muratartim@gmail.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package equinox.task;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import equinox.network.NetworkWatcher;
import equinox.task.InternalEquinoxTask.ShortRunningTask;
import equinox.utility.exception.PermissionDeniedException;
import equinox.utility.exception.ServerDatabaseQueryFailedException;
import equinoxServer.remote.message.DatabaseQueryFailed;
import equinoxServer.remote.message.DatabaseQueryMessage;
import equinoxServer.remote.message.DatabaseQueryPermissionDenied;
import equinoxServer.remote.message.GetAircraftSectionsForPilotPointsRequest;
import equinoxServer.remote.message.GetAircraftSectionsForPilotPointsResponse;

/**
 * Class for get aircraft sections for pilot points task.
 *
 * @author Murat Artim
 * @date 9 Aug 2017
 * @time 22:00:32
 *
 */
public class GetAircraftSectionsForPilotPoints extends InternalEquinoxTask<ArrayList<String>> implements ShortRunningTask, DatabaseQueryListenerTask {

	/** Serial ID. */
	private static final long serialVersionUID = 1L;

	/** Requesting panel. */
	private final PilotPointAircraftSectionRequestingPanel panel_;

	/** Aircraft program. */
	private final String program_;

	/** Server query completion indicator. */
	private final AtomicBoolean isQueryCompleted;

	/** Server query message. */
	private final AtomicReference<DatabaseQueryMessage> serverMessageRef;

	/**
	 * Creates get aircraft sections from global database task.
	 *
	 * @param panel
	 *            Requesting panel.
	 * @param program
	 *            Aircraft program.
	 */
	public GetAircraftSectionsForPilotPoints(PilotPointAircraftSectionRequestingPanel panel, String program) {
		panel_ = panel;
		program_ = program;
		isQueryCompleted = new AtomicBoolean();
		serverMessageRef = new AtomicReference<>(null);
	}

	@Override
	public boolean canBeCancelled() {
		return false;
	}

	@Override
	public String getTaskTitle() {
		return "Get aircraft sections for program '" + program_ + "'";
	}

	@Override
	public void respondToDatabaseQueryMessage(DatabaseQueryMessage message) throws Exception {
		processServerDatabaseQueryMessage(message, this, serverMessageRef, isQueryCompleted);
	}

	@Override
	protected ArrayList<String> call() throws Exception {

		// update progress info
		updateTitle("Retreiving aircraft sections for '" + program_ + "' from database...");
		updateMessage("Please wait...");

		// initialize variables
		NetworkWatcher watcher = null;
		boolean removeListener = false;

		try {

			// create request message
			GetAircraftSectionsForPilotPointsRequest request = new GetAircraftSectionsForPilotPointsRequest();
			request.setDatabaseQueryID(hashCode());
			request.setProgram(program_);

			// disable task canceling
			taskPanel_.updateCancelState(false);

			// register to network watcher and send analysis request
			watcher = taskPanel_.getOwner().getOwner().getNetworkWatcher();
			watcher.addDatabaseQueryListener(this);
			removeListener = true;
			watcher.sendMessage(request);

			// wait for query to complete
			waitForQuery(this, isQueryCompleted);

			// remove from network watcher
			watcher.removeDatabaseQueryListener(this);
			removeListener = false;

			// enable task canceling
			taskPanel_.updateCancelState(true);

			// task cancelled
			if (isCancelled())
				return null;

			// get query message
			DatabaseQueryMessage message = serverMessageRef.get();

			// permission denied
			if (message instanceof DatabaseQueryPermissionDenied)
				throw new PermissionDeniedException(((DatabaseQueryPermissionDenied) message).getPermission());

			// query failed
			else if (message instanceof DatabaseQueryFailed)
				throw new ServerDatabaseQueryFailedException((DatabaseQueryFailed) message);

			// query succeeded
			else if (message instanceof GetAircraftSectionsForPilotPointsResponse)
				return ((GetAircraftSectionsForPilotPointsResponse) message).getSections();

			// no aircraft program found
			throw new Exception("No aircraft program found for pilot points.");
		}

		// remove from network watcher
		finally {
			if ((watcher != null) && removeListener) {
				watcher.removeDatabaseQueryListener(this);
			}
		}
	}

	@Override
	protected void succeeded() {

		// call ancestor
		super.succeeded();

		// set results to bug report panel
		try {
			panel_.setAircraftSectionsForPilotPoints(get());
		}

		// exception occurred
		catch (InterruptedException | ExecutionException e) {
			handleResultRetrievalException(e);
		}
	}

	/**
	 * Interface for aircraft section requesting panel.
	 *
	 * @author Murat Artim
	 * @date 26 Jul 2017
	 * @time 13:32:57
	 *
	 */
	public interface PilotPointAircraftSectionRequestingPanel {

		/**
		 * Sets given list of aircraft sections.
		 *
		 * @param sections
		 *            Aircraft sections.
		 */
		void setAircraftSectionsForPilotPoints(Collection<String> sections);
	}
}