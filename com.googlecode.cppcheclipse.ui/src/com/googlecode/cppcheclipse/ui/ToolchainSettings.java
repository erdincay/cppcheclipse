package com.googlecode.cppcheclipse.ui;

import java.io.File;
import java.net.URI;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.cdt.core.CCorePlugin;
import org.eclipse.cdt.core.cdtvariables.CdtVariableException;
import org.eclipse.cdt.core.cdtvariables.ICdtVariableManager;
import org.eclipse.cdt.core.model.CoreModel;
import org.eclipse.cdt.core.settings.model.ICConfigurationDescription;
import org.eclipse.cdt.core.settings.model.ICFolderDescription;
import org.eclipse.cdt.core.settings.model.ICLanguageSetting;
import org.eclipse.cdt.core.settings.model.ICLanguageSettingEntry;
import org.eclipse.cdt.core.settings.model.ICProjectDescription;
import org.eclipse.cdt.core.settings.model.ICSettingEntry;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IPathVariableManager;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;

import com.googlecode.cppcheclipse.core.CppcheclipsePlugin;
import com.googlecode.cppcheclipse.core.IToolchainSettings;
import com.googlecode.cppcheclipse.core.Symbol;

/**
 * Getting some settings done in the toolchain of CDT
 * 
 * @see <a
 *      href="http://cdt-devel-faq.wikidot.com/#toc21">http://cdt-devel-faq.wikidot.com/#toc21</a>
 * @author kwindszus
 * 
 */
public class ToolchainSettings implements IToolchainSettings {
	private final List<ICLanguageSetting> languageSettings;
	private final ICConfigurationDescription activeConfiguration;
	private final IProject project;
	private final IWorkspaceRoot root;
	private final ICdtVariableManager variableManager;

	public ToolchainSettings(IProject project) throws IllegalStateException {
		languageSettings = new LinkedList<ICLanguageSetting>();
		this.project = project;

		variableManager = CCorePlugin.getDefault().getCdtVariableManager();

		ICProjectDescription projectDescription = CoreModel.getDefault()
				.getProjectDescription(project);
		if (projectDescription == null) {
			throw new IllegalStateException("No valid CDT project given!");
		}

		activeConfiguration = projectDescription.getActiveConfiguration(); // or
																			// another
																			// config
		if (activeConfiguration == null) {
			throw new IllegalStateException(
					"No valid active configuration found!");
		}
		ICFolderDescription folderDescription = activeConfiguration
				.getRootFolderDescription(); // or use
		// getResourceDescription(IResource),
		// or pick one from
		// getFolderDescriptions()
		ICLanguageSetting[] allLanguageSettings = folderDescription
				.getLanguageSettings();

		// fetch the include settings from the first tool which supports c
		for (ICLanguageSetting languageSetting : allLanguageSettings) {
			String extensions[] = languageSetting.getSourceExtensions();
			for (String extension : extensions) {
				if ("cpp".equalsIgnoreCase(extension)) { //$NON-NLS-1$
					languageSettings.add(languageSetting);
				}
			}
		}

		root = ResourcesPlugin.getWorkspace().getRoot();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.cppcheclipse.ui.IToolchainSettings#getUserIncludes()
	 */
	public Collection<File> getUserIncludes() {
		return getIncludes(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see
	 * com.googlecode.cppcheclipse.ui.IToolchainSettings#getSystemIncludes()
	 */
	public Collection<File> getSystemIncludes() {
		return getIncludes(false);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.cppcheclipse.ui.IToolchainSettings#getUserSymbols()
	 */
	public Collection<Symbol> getUserSymbols() {
		return getSymbols(true);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.googlecode.cppcheclipse.ui.IToolchainSettings#getSystemSymbols()
	 */
	public Collection<Symbol> getSystemSymbols() {
		return getSymbols(false);
	}

	private Collection<File> resolveIncludePath(String includePath,
			IPathVariableManager pathVariableManager)
			throws CdtVariableException {
		Collection<File> result = new LinkedList<File>();
		
		// try to resolve CDT variables
		includePath = variableManager.resolveValue(includePath, null,
				null, activeConfiguration);

		// convert file path to URI (the path string might contain spaces)
		URI includePathUri = new File(includePath).toURI();
		
		// try to resolve path variables
		includePathUri = pathVariableManager.resolveURI(includePathUri);

		// path must be absolute at this point
		if (includePathUri.isAbsolute()) {
			// resolve workspace paths, since it may contain linked resources
			IFile[] files = root.findFilesForLocationURI(includePathUri);
			// if we could resolve the file
			if (files.length > 0) {
				for (IFile file : files) {
					result.add(new File(file.getRawLocationURI()));
				}
			} else {
				// otherwise just take the whole file
				result.add(new File(includePathUri));
			}
		} else {
			// if URI is not absolute or has no schema, just
			// take the string (File constructor only accepts
			// absolute URIs)
			result.add(new File(includePath));
		}
		return result;
	}

	/**
	 * Gets all the include folders from the current configuration.
	 * 
	 * @param onlyUserDefined
	 *            if true, only user-defined includes are returned, otherwise
	 *            only system-defined.
	 * @return all include folders in a list
	 */
	protected Collection<File> getIncludes(boolean onlyUserDefined) {
		Collection<File> paths = new LinkedList<File>();
		URI workspaceUri = project.getWorkspace().getRoot().getLocationURI();

		// evaluate path variables
		IPathVariableManager pathVariableManager = project.getWorkspace()
				.getPathVariableManager();

		for (ICLanguageSetting languageSetting : languageSettings) {
			ICLanguageSettingEntry[] includePathSettings = languageSetting
					.getSettingEntries(ICSettingEntry.INCLUDE_PATH);
			for (ICLanguageSettingEntry includePathSetting : includePathSettings) {
				// only regard user-specified include paths or only
				// system include paths
				if ((!includePathSetting.isBuiltIn() && onlyUserDefined)
						|| (includePathSetting.isBuiltIn() && !onlyUserDefined)) {
					String includePath;

					// make path absolute
					if ((includePathSetting.getFlags() & ICSettingEntry.VALUE_WORKSPACE_PATH) == ICSettingEntry.VALUE_WORKSPACE_PATH) {
						includePath = workspaceUri
								+ includePathSetting.getValue();
					} else {
						includePath = includePathSetting.getValue();
					}
					try {
						paths.addAll(resolveIncludePath(includePath, pathVariableManager));
					} catch (CdtVariableException e) {
						CppcheclipsePlugin.logError("Invalid include path '"
								+ includePathSetting.getValue() + "'", e);
					}
				}
			}
		}
		return paths;
	}

	/**
	 * Gets all the macros from the current configuration.
	 * 
	 * @param onlyUserDefined
	 *            if true, only user-defined macros are returned, otherwise only
	 *            system-defined.
	 * @return all macros in a list
	 */
	protected Collection<Symbol> getSymbols(boolean onlyUserDefined) {
		Collection<Symbol> symbols = new LinkedList<Symbol>();
		for (ICLanguageSetting languageSetting : languageSettings) {
			ICLanguageSettingEntry[] macroSettings = languageSetting
					.getSettingEntries(ICSettingEntry.MACRO);
			for (ICLanguageSettingEntry macroSetting : macroSettings) {

				// only regard user-specified include paths or only
				// system include paths
				if ((!macroSetting.isBuiltIn() && onlyUserDefined)
						|| (macroSetting.isBuiltIn() && !onlyUserDefined)) {
					Symbol symbol = new Symbol(macroSetting.getName(),
							macroSetting.getValue(), true,
							macroSetting.isBuiltIn());
					symbols.add(symbol);
				}
			}
		}
		return symbols;
	}
}
