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

import java.io.File;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.concurrent.ExecutionException;

import equinox.Equinox;
import equinox.data.fileType.StressSequence;
import equinox.process.SaveSTH;
import equinox.serverUtilities.Permission;
import equinox.task.InternalEquinoxTask.LongRunningTask;
import equinox.task.automation.SingleInputTask;
import equinox.task.automation.SingleInputTaskOwner;

/**
 * Class for save spectrum as STH task.
 *
 * @author Murat Artim
 * @date Jan 7, 2014
 * @time 2:13:14 PM
 */
public class SaveStressSequenceAsSTH extends InternalEquinoxTask<Path> implements LongRunningTask, SingleInputTask<StressSequence>, SingleInputTaskOwner<Path> {

	/** Stress sequence. */
	private StressSequence stressSequence;

	/** Output file. */
	private final File output;

	/** Automatic tasks. */
	private HashMap<String, SingleInputTask<Path>> automaticTasks_ = null;

	/** Automatic task execution mode. */
	private boolean executeAutomaticTasksInParallel_ = true;

	/**
	 * Creates save spectrum as STH task.
	 *
	 * @param stressSequence
	 *            Stress sequence to save. Can be null for automatic execution.
	 * @param output
	 *            Output file.
	 */
	public SaveStressSequenceAsSTH(StressSequence stressSequence, File output) {
		this.stressSequence = stressSequence;
		this.output = output;
	}

	@Override
	public void setAutomaticTaskExecutionMode(boolean isParallel) {
		executeAutomaticTasksInParallel_ = isParallel;
	}

	@Override
	public void addSingleInputTask(String taskID, SingleInputTask<Path> task) {
		if (automaticTasks_ == null) {
			automaticTasks_ = new HashMap<>();
		}
		automaticTasks_.put(taskID, task);
	}

	@Override
	public HashMap<String, SingleInputTask<Path>> getSingleInputTasks() {
		return automaticTasks_;
	}

	@Override
	public void setAutomaticInput(StressSequence input) {
		this.stressSequence = input;
	}

	@Override
	public String getTaskTitle() {
		return "Save stress sequence";
	}

	@Override
	public boolean canBeCancelled() {
		return true;
	}

	@Override
	protected Path call() throws Exception {

		// check permission
		checkPermission(Permission.SAVE_FILE);

		// start process
		try (Connection connection = Equinox.DBC_POOL.getConnection()) {
			new SaveSTH(this, stressSequence, output).start(connection);
		}

		// return output path
		return output.toPath();
	}

	@Override
	protected void succeeded() {

		// call ancestor
		super.succeeded();

		try {

			// get output file
			Path file = get();

			// execute automatic tasks
			if (automaticTasks_ != null) {
				for (SingleInputTask<Path> task : automaticTasks_.values()) {
					task.setAutomaticInput(file);
					if (executeAutomaticTasksInParallel_) {
						taskPanel_.getOwner().runTaskInParallel((InternalEquinoxTask<?>) task);
					}
					else {
						taskPanel_.getOwner().runTaskSequentially((InternalEquinoxTask<?>) task);
					}
				}
			}
		}

		// exception occurred
		catch (InterruptedException | ExecutionException e) {
			handleResultRetrievalException(e);
		}
	}
}
