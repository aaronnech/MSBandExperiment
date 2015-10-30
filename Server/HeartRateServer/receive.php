<?php

// Storage file base names
$HEART_RATE_FILE = 'heartRate.csv';
$ACCELEROMETER_FILE = 'accelerometer.csv';

if ($_SERVER['REQUEST_METHOD'] !== 'POST') {
    die();
}

// Security check (weak)
$STORE_TOKEN = 'saljfn73ksfDUCKDUCKGOOSEksdjf23';
$DELETE_TOKEN = 'jhkjshf88838939843FOOBARjhsdkfsdkjfhskdjhf';
$requestToken = $_POST['token'];

if ($requestToken == $DELETE_TOKEN) {
	// Delete all CSV files
	array_map('unlink', glob('*' . $HEART_RATE_FILE));
	array_map('unlink', glob('*' . $ACCELEROMETER_FILE));
	die();
} else if ($requestToken !== $STORE_TOKEN) {
	die();
}

// This is a payload, Process the data

// Get the data string / session id
$data = urldecode($_POST['payload']);
$sessionId = $_POST['session'];

if (!$data || empty($data)) {
	die();
}

// Explode into CSV rows
$rows = explode(';', $data);

mkdir($sessionId);

// Deposit each row as a CSV row into destination files
foreach ($rows as $row) {
	$fields = explode('```///', $row);
	next($fields);
	$type = current($fields);
	$bandID = reset($fields);

	$heartRate = fopen($sessionId . '/' . $bandID . '-' . $HEART_RATE_FILE, 'a');
	$accelerometer = fopen($sessionId . '/' . $bandID . '-' . $ACCELEROMETER_FILE, 'a');

	$destination = null;
	switch ($type) {
		case 'HeartRate':
			$destination = $heartRate;
			break;

		case 'Accelerometer':
			$destination = $accelerometer;
			break;
	}

	if ($destination !== null) {
 		fputcsv($destination, $fields);
	}

	fclose($heartRate);
	fclose($accelerometer);
}