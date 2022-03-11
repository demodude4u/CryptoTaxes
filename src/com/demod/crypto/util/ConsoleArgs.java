package com.demod.crypto.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.Arrays;
import java.util.Scanner;

import org.json.JSONException;
import org.json.JSONObject;

public class ConsoleArgs {
	@FunctionalInterface
	private interface ObjectOpt<T> {
		public T opt(JSONObject json, String key, T defaultValue);
	}

	private static final File JSON_FILE = new File("data/lastRunArgs.json");

	private static JSONObject json = null;

	private static Scanner scanner = new Scanner(System.in);

	public static boolean argBoolean(String program, String label, String[] args, int argIndex, boolean defaultValue) {
		if (argIndex < args.length) {
			return Boolean.parseBoolean(args[argIndex]);
		}
		boolean pickedValue = getLastPick(program, label, JSONObject::optBoolean, defaultValue);
		System.out.print("Please Enter " + label + " [" + pickedValue + "]: ");
		String input = scanner.nextLine();
		if (!input.isBlank()) {
			pickedValue = Boolean.parseBoolean(input);
			setLastPick(program, label, pickedValue);
		}
		return pickedValue;
	}

	public static int argInt(String program, String label, String[] args, int argIndex, int defaultValue) {
		if (argIndex < args.length) {
			return Integer.parseInt(args[argIndex]);
		}
		int pickedValue = getLastPick(program, label, JSONObject::optInt, defaultValue);
		System.out.print("Please Enter " + label + " [" + pickedValue + "]: ");
		String input = scanner.nextLine();
		if (!input.isBlank()) {
			pickedValue = Integer.parseInt(input);
			setLastPick(program, label, pickedValue);
		}
		return pickedValue;
	}

	public static String argStringChoice(String program, String label, String[] args, int argIndex, String defaultValue,
			String[] choices) {
		if (argIndex < args.length) {
			return args[argIndex];
		}
		String pickedValue = getLastPick(program, label, JSONObject::optString, defaultValue);
		System.out.println(label + " Choices: " + Arrays.toString(choices));
		System.out.print("Please Enter " + label + " [" + pickedValue + "]: ");
		String input = scanner.nextLine();
		if (!input.isBlank()) {
			pickedValue = input;
			setLastPick(program, label, pickedValue);
		}
		return pickedValue;
	}

	private static <T> T getLastPick(String group, String key, ObjectOpt<T> opt, T defaultValue) {
		if (json == null) {
			json = loadJson(JSON_FILE);
		}
		JSONObject groupJson = json.optJSONObject(group);
		if (groupJson != null) {
			return opt.opt(groupJson, key, defaultValue);
		} else {
			return defaultValue;
		}
	}

	private static JSONObject loadJson(File jsonFile) {
		try {
			return new JSONObject(Files.readString(jsonFile.toPath()));
		} catch (NoSuchFileException e) {
			System.out.println("Creating new json file... " + jsonFile.getName());
			return new JSONObject();
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			return new JSONObject();
		}
	}

	private static void setLastPick(String group, String key, Object pickedValue) {
		JSONObject groupJson = json.optJSONObject(group);
		if (groupJson == null) {
			json.put(group, groupJson = new JSONObject());
		}
		groupJson.put(key, pickedValue);
		try {
			Files.writeString(JSON_FILE.toPath(), json.toString(2));
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			System.exit(-1);
		}
	}
}
