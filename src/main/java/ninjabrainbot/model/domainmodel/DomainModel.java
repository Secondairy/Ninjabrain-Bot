package ninjabrainbot.model.domainmodel;

import java.util.ArrayList;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import ninjabrainbot.util.Assert;

/**
 * Keeps track of all DataComponents, to manage write lock to them and monitors changes so that undo works.
 */
public class DomainModel implements IDomainModel {

	private final ReentrantReadWriteLock lock;
	private final ArrayList<IDataComponent<?>> dataComponents;
	private final DomainModelHistory domainModelHistory;

	private boolean isFullyInitialized = false;

	public DomainModel() {
		lock = new ReentrantReadWriteLock();
		dataComponents = new ArrayList<>();
		domainModelHistory = new DomainModelHistory(dataComponents, 10);
	}

	@Override
	public void registerDataComponent(IDataComponent<?> dataComponent) {
		Assert.isFalse(isFullyInitialized, "New DataComponents cannot be registered in the DomainModel after it has been fully initialized.");
		dataComponents.add(dataComponent);
	}

	@Override
	public void acquireWriteLock() {
		lock.writeLock().lock();
		if (!isFullyInitialized)
			finishInitialization();
	}

	@Override
	public void releaseWriteLock() {
		releaseWriteLock(true);
	}

	private void releaseWriteLock(boolean saveSnapshotOfNewState) {
		if (saveSnapshotOfNewState)
			domainModelHistory.saveSnapshotIfUniqueFromLastSnapshot();
		lock.writeLock().unlock();
	}

	@Override
	public void reset() {
		dataComponents.forEach(IDataComponent::reset);
	}

	@Override
	public void undoUnderWriteLock() {
		acquireWriteLock();
		try {
			if (domainModelHistory.hasPreviousSnapshot())
				domainModelHistory.moveToPreviousSnapshotAndGet().restoreDomainModelToStateAtSnapshot();
		} finally {
			releaseWriteLock(false);
		}
	}

	@Override
	public void redoUnderWriteLock() {
		acquireWriteLock();
		try {
			if (domainModelHistory.hasNextSnapshot())
				domainModelHistory.moveToNextSnapshotAndGet().restoreDomainModelToStateAtSnapshot();
		} finally {
			releaseWriteLock(false);
		}
	}

	@Override
	public boolean isReset() {
		return dataComponents.stream().allMatch(IDataComponent::isReset);
	}

	@Override
	public Runnable applyWriteLock(Runnable runnable) {
		return () -> runUnderWriteLock(runnable);
	}

	private void runUnderWriteLock(Runnable runnable) {
		acquireWriteLock();
		try {
			runnable.run();
		} finally {
			releaseWriteLock(false);
		}
	}

	public void notifyDataComponentToBeModified() {
		if (!lock.isWriteLocked())
			throw new IllegalModificationException("DataComponents cannot be changed without a write lock, create and execute an Action instead of trying to modify the DataComponent directly.");
		if (!lock.isWriteLockedByCurrentThread())
			throw new IllegalModificationException("Modification was attempted by thread " + Thread.currentThread().getName() + ", while the write lock is held by another thread.");
	}

	private void finishInitialization() {
		domainModelHistory.initialize();
		isFullyInitialized = true;
	}

}
